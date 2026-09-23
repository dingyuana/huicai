package com.huicai.base.voucher.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.agency.tenant.entity.EnterpriseEntity;
import com.huicai.agency.tenant.mapper.EnterpriseMapper;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.event.ServiceProgressStageEvent;
import com.huicai.base.voucher.dto.SubjectProfitTotalRow;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.entity.CloseLogEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.CloseLogMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.voucher.service.PeriodCloseService;
import com.huicai.base.voucher.service.VoucherService;
import com.huicai.base.voucher.constant.VoucherType;
import com.huicai.base.balance.entity.SubjectBalanceEntity;
import com.huicai.base.balance.service.SubjectBalanceService;
import com.huicai.base.report.service.ReportService;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.system.service.PeriodService;
import com.huicai.base.system.service.SubjectService;
import com.huicai.base.system.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PeriodCloseServiceImpl implements PeriodCloseService {

    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final SubjectBalanceService subjectBalanceService;
    private final PeriodService periodService;
    private final SubjectService subjectService;
    private final SubjectMapper subjectMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final ReportService reportService;
    private final ApplicationEventPublisher eventPublisher; // P79：REVIEW 节点推进事件
    private final CloseLogMapper closeLogMapper; // P85：结账日志（t_close_log），须追加在末尾避免改既有构造顺序
    private final VoucherService voucherService; // P84：一键审核记账（batchSubmit/batchAudit/batchPost）

    @Override
    public Map<String, Object> checkBeforeClose(String period) {
        Map<String, Object> result = new HashMap<>();
        List<String> issues = new ArrayList<>();

        PeriodEntity periodEntity = findPeriod(period);
        if ("closed".equals(periodEntity.getStatus())) {
            throw BusinessException.badRequest("期间已结账");
        }
        if ("locked".equals(periodEntity.getStatus())) {
            throw BusinessException.badRequest("期间已锁定, 不能结账");
        }

        try {
            validateCloseOrder(period);
        } catch (BusinessException e) {
            issues.add(e.getMessage());
        }

        Long unposted = voucherMapper.selectCount(
                new LambdaQueryWrapper<VoucherEntity>()
                        .eq(VoucherEntity::getPeriod, period)
                        .ne(VoucherEntity::getStatus, "POSTED")
                        .eq(VoucherEntity::getDeleted, 0));
        if (unposted > 0) {
            issues.add("存在 " + unposted + " 张未记账凭证");
        }

        Map<String, Object> trial = subjectBalanceService.checkTrialBalance(period);
        if (!Boolean.TRUE.equals(trial.get("balanced"))) {
            issues.add("试算不平衡, 借方发生 " + trial.get("totalDebitTotal") + " / 贷方发生 " + trial.get("totalCreditTotal"));
        }

        Map<String, Object> balanceSheet = reportService.balanceSheet(period);
        if (!Boolean.TRUE.equals(balanceSheet.get("balanced"))) {
            issues.add("资产负债表不平衡（差额 " + balanceSheet.get("diff")
                    + "），请核对差异科目后再结账");
        }

        Long unReversed = voucherMapper.selectCount(
                new LambdaQueryWrapper<VoucherEntity>()
                        .eq(VoucherEntity::getPeriod, period)
                        .isNotNull(VoucherEntity::getReversedFrom)
                        .eq(VoucherEntity::getStatus, "DRAFT")
                        .eq(VoucherEntity::getDeleted, 0));
        if (unReversed > 0) {
            issues.add("存在 " + unReversed + " 张草稿状态的红冲凭证未提交");
        }

        result.put("passed", issues.isEmpty());
        result.put("issues", issues);
        result.put("trialBalance", trial);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generateProfitCarryOver(String period, Long userId) {
        findPeriod(period);

        // 幂等保护: 该期间已存在结转凭证(未删除且非红冲)时禁止重复结转
        Long existing = voucherMapper.selectCount(
                new LambdaQueryWrapper<VoucherEntity>()
                        .likeRight(VoucherEntity::getVoucherNo, "CLOSE-" + period)
                        .isNull(VoucherEntity::getReversedFrom)
                        .eq(VoucherEntity::getDeleted, 0));
        if (existing != null && existing > 0) {
            throw BusinessException.badRequest("期间 " + period + " 已存在 " + existing + " 张结转凭证, 请勿重复结转");
        }

        // 本年利润科目（4103）
        Subject profitSubject = subjectMapper.selectOne(
                new LambdaQueryWrapper<Subject>()
                        .eq(Subject::getCode, "4103")
                        .eq(Subject::getDeleted, 0)
                        .last("LIMIT 1"));
        if (profitSubject == null) {
            throw BusinessException.badRequest("未配置本年利润科目(4103), 无法生成结转凭证");
        }

        // P85 性能修复：一条聚合 SQL 按期间取损益科目(6xx)借贷发生额合计。
        // 替代旧实现 selectList(null) 全表扫描 + 对每条 selectById(N+1) + Java 里再按 6xx/期间过滤。
        // 租户隔离由 EnterpriseDataPermissionInterceptor 自动注入（t_voucher/t_subject/t_voucher_entry 均非共享表）。
        List<SubjectProfitTotalRow> profitTotals = voucherEntryMapper.selectProfitSubjectTotals(period);
        if (profitTotals.isEmpty()) {
            throw BusinessException.badRequest("期间 " + period + " 无可结转的损益数据");
        }

        // 构建结转凭证：损益类科目余额结转到本年利润
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo("CLOSE-" + period + "-" + System.currentTimeMillis() % 10000);
        voucher.setPeriod(period);
        voucher.setVoucherTypeId(VoucherType.ZZ);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary("自动结转损益: " + period);
        voucher.setCreatedBy(userId);
        voucher.setCreatedAt(LocalDateTime.now());
        voucherMapper.insert(voucher);

        BigDecimal totalD = BigDecimal.ZERO;
        BigDecimal totalC = BigDecimal.ZERO;
        int sort = 1;
        for (SubjectProfitTotalRow t : profitTotals) {
            // 本年利润(4103)本身不参与损益结转——它是结转目标，不是源
            if (profitSubject.getId().equals(t.getSubjectId())) continue;
            // 聚合查询已按 s.code LIKE '6%' 过滤出损益类科目，此处双保险
            if (t.getCode() == null || !t.getCode().startsWith("6")) continue;
            BigDecimal debit = t.getDebitTotal() == null ? BigDecimal.ZERO : t.getDebitTotal();
            BigDecimal credit = t.getCreditTotal() == null ? BigDecimal.ZERO : t.getCreditTotal();
            BigDecimal net = debit.subtract(credit);

            VoucherEntryEntity line;
            if ("credit".equals(t.getDirection())) {
                // 收入类: 贷方余额反向结转 -> 借收入科目 / 贷本年利润
                if (net.signum() >= 0) continue; // 收入科目净额为借(异常)时跳过
                BigDecimal amount = net.abs();
                line = entryOf(voucher.getId(), t.getSubjectId(), amount, BigDecimal.ZERO, sort++,
                        "结转收入 " + t.getName() + " 至本年利润");
                voucherEntryMapper.insert(line);
                line = entryOf(voucher.getId(), profitSubject.getId(), BigDecimal.ZERO, amount, sort++,
                        "收入结转 " + t.getName());
                voucherEntryMapper.insert(line);
                totalD = totalD.add(amount);
                totalC = totalC.add(amount);
            } else {
                // 费用类: 借方余额反向结转 -> 借本年利润 / 贷费用科目
                if (net.signum() <= 0) continue;
                BigDecimal amount = net;
                line = entryOf(voucher.getId(), profitSubject.getId(), amount, BigDecimal.ZERO, sort++,
                        "结转费用 " + t.getName() + " 至本年利润");
                voucherEntryMapper.insert(line);
                line = entryOf(voucher.getId(), t.getSubjectId(), BigDecimal.ZERO, amount, sort++,
                        "费用结转 " + t.getName());
                voucherEntryMapper.insert(line);
                totalD = totalD.add(amount);
                totalC = totalC.add(amount);
            }
        }

        if (sort == 1) {
            throw BusinessException.badRequest("期间 " + period + " 无损益类科目余额, 无需结转");
        }

        voucher.setTotalDebit(totalD);
        voucher.setTotalCredit(totalC);
        voucherMapper.updateById(voucher);

        log.info("生成损益结转凭证: id={}, period={}, debit={}, credit={}",
                voucher.getId(), period, totalD, totalC);
        return voucher.getId();
    }

    /** 利润分配提取比例 */
    private static final BigDecimal PROFIT_DISTRIBUTION_RATIO = new BigDecimal("0.10");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generateProfitDistribution(String period, Long userId) {
        findPeriod(period);

        // 幂等保护: 该期间已存在利润分配凭证(未删除且非红冲)时禁止重复
        Long existing = voucherMapper.selectCount(
                new LambdaQueryWrapper<VoucherEntity>()
                        .likeRight(VoucherEntity::getVoucherNo, "DISTRIB-" + period)
                        .isNull(VoucherEntity::getReversedFrom)
                        .eq(VoucherEntity::getDeleted, 0));
        if (existing != null && existing > 0) {
            throw BusinessException.badRequest("期间 " + period + " 已存在 " + existing + " 张利润分配凭证, 请勿重复结转");
        }

        // 本年利润科目（4103）
        Subject profit = subjectMapper.selectOne(
                new LambdaQueryWrapper<Subject>()
                        .eq(Subject::getCode, "4103")
                        .eq(Subject::getDeleted, 0)
                        .last("LIMIT 1"));
        if (profit == null) {
            throw BusinessException.badRequest("未配置本年利润科目(4103), 无法生成利润分配凭证");
        }

        // 盈余公积科目（4101）与利润分配科目（4104）
        Subject surplus = subjectMapper.selectOne(
                new LambdaQueryWrapper<Subject>()
                        .eq(Subject::getCode, "4101")
                        .eq(Subject::getDeleted, 0)
                        .last("LIMIT 1"));
        Subject distrib = subjectMapper.selectOne(
                new LambdaQueryWrapper<Subject>()
                        .eq(Subject::getCode, "4104")
                        .eq(Subject::getDeleted, 0)
                        .last("LIMIT 1"));
        if (surplus == null || distrib == null) {
            throw BusinessException.badRequest("未配置盈余公积(4101)或利润分配(4104)科目, 无法生成利润分配凭证");
        }

        // 从科目余额表读取本年利润期末余额
        List<SubjectBalanceEntity> balances = subjectBalanceService.queryByPeriod(period);
        BigDecimal netProfit = null;
        for (SubjectBalanceEntity b : balances) {
            if (profit.getId().equals(b.getSubjectId())) {
                netProfit = b.getEndBalance();
                break;
            }
        }
        if (netProfit == null) {
            throw BusinessException.badRequest("期间 " + period + " 本年利润无余额数据, 请先完成损益结转过账");
        }
        if (netProfit.compareTo(BigDecimal.ZERO) <= 0) {
            throw BusinessException.badRequest("期间 " + period + " 净利润为 " + netProfit + ", 亏损无需分配");
        }

        // 按比例提取盈余公积
        BigDecimal amount = netProfit.multiply(PROFIT_DISTRIBUTION_RATIO)
                .setScale(2, RoundingMode.HALF_UP);

        // 生成 DRAFT 凭证
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo("DISTRIB-" + period + "-" + System.currentTimeMillis() % 10000);
        voucher.setPeriod(period);
        voucher.setVoucherTypeId(VoucherType.ZZ);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary("自动利润分配: " + period);
        voucher.setCreatedBy(userId);
        voucher.setCreatedAt(LocalDateTime.now());
        voucher.setTotalDebit(amount);
        voucher.setTotalCredit(amount);
        voucherMapper.insert(voucher);

        // 借：利润分配(4104)  贷：盈余公积(4101)
        VoucherEntryEntity debitLine = entryOf(voucher.getId(), distrib.getId(),
                amount, BigDecimal.ZERO, 1, "提取盈余公积");
        voucherEntryMapper.insert(debitLine);
        VoucherEntryEntity creditLine = entryOf(voucher.getId(), surplus.getId(),
                BigDecimal.ZERO, amount, 2, "提取盈余公积");
        voucherEntryMapper.insert(creditLine);

        log.info("生成利润分配凭证: id={}, period={}, netProfit={}, amount={}",
                voucher.getId(), period, netProfit, amount);
        return voucher.getId();
    }

    private VoucherEntryEntity entryOf(Long voucherId, Long subjectId, BigDecimal debit, BigDecimal credit, int sort, String summary) {
        VoucherEntryEntity line = new VoucherEntryEntity();
        line.setVoucherId(voucherId);
        line.setSubjectId(subjectId);
        line.setDebit(debit);
        line.setCredit(credit);
        line.setSummary(summary);
        line.setSortOrder(sort);
        return line;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closePeriod(String period, Long userId) {
        validateCloseOrder(period);
        Map<String, Object> check = checkBeforeClose(period);
        if (!Boolean.TRUE.equals(check.get("passed"))) {
            throw BusinessException.badRequest("结账检查未通过: " + check.get("issues"));
        }

        long closeStart = System.currentTimeMillis();
        PeriodEntity periodEntity = findPeriod(period);
        periodEntity.setStatus("closed");
        periodEntity.setUpdatedBy(userId);
        periodEntity.setUpdatedAt(LocalDateTime.now());
        periodService.updateById(periodEntity);

        log.info("期间已结账: period={}, userId={}", period, userId);
        appendCloseLog(period, "CLOSE", userId, true, "期间 " + period + " 已结账",
                System.currentTimeMillis() - closeStart);

        // P79 REVIEW：本期结账完成 → 发事件推进 REVIEW 节点（监听器按 agencyId 降级；无安全上下文时静默跳过，绝不影响业务）
        try {
            Long agencyId = SecurityUtils.getCurrentAgencyId();
            Long enterpriseId = SecurityUtils.getCurrentEnterpriseId();
            eventPublisher.publishEvent(new ServiceProgressStageEvent(
                    agencyId, enterpriseId, period, ServiceProgressStageEvent.STAGE_REVIEW));
            log.info("P79 结账完成发 REVIEW 事件: agencyId={} enterpriseId={} period={}",
                    agencyId, enterpriseId, period);
        } catch (Exception ex) {
            log.debug("P79 REVIEW 事件跳过（无安全上下文或发布失败，不影响业务）: period={}", period);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reopenPeriod(String period, Long userId) {
        long reopenStart = System.currentTimeMillis();
        PeriodEntity periodEntity = findPeriod(period);
        if (!"closed".equals(periodEntity.getStatus())) {
            throw BusinessException.badRequest("仅已结账期间可反结账");
        }
        validateReopenOrder(period);
        // P87: 反结账前检查该期间是否存在未过账(DRAFT)的自动结转凭证(CLOSE-/DISTRIB-/DEPR-)。
        // 半成品结转凭证不能留在重新打开的期间里——要求人工先处理(删除草稿或完成记账)再反结账，
        // 防 reopen 后留下"垃圾草稿"污染下期期初。已 POSTED 的结转凭证保留(冲销走红冲流程)。
        for (String prefix : new String[]{"CLOSE-", "DISTRIB-", "DEPR-"}) {
            Long pending = voucherMapper.selectCount(
                    new LambdaQueryWrapper<VoucherEntity>()
                            .likeRight(VoucherEntity::getVoucherNo, prefix + period)
                            .eq(VoucherEntity::getStatus, "DRAFT")
                            .isNull(VoucherEntity::getReversedFrom)
                            .eq(VoucherEntity::getDeleted, 0));
            if (pending != null && pending > 0) {
                throw BusinessException.badRequest(
                        "期间 " + period + " 存在 " + pending + " 张未过账的 " + prefix
                                + " 结转凭证，请先删除草稿或完成记账后再反结账");
            }
        }
        periodEntity.setStatus("open");
        periodEntity.setUpdatedBy(userId);
        periodEntity.setUpdatedAt(LocalDateTime.now());
        periodService.updateById(periodEntity);
        log.info("期间已反结账: period={}, userId={}", period, userId);
        appendCloseLog(period, "REOPEN", userId, true, "期间 " + period + " 已反结账",
                System.currentTimeMillis() - reopenStart);
    }

    @Override
    public List<Map<String, Object>> listCloseLog(String period) {
        // P85：读 t_close_log 落库日志（替代原空实现）。
        // 只读查询，失败时返回空列表而不是抛异常——日志查询不应阻断结账主流程。
        try {
            List<CloseLogEntity> logs = closeLogMapper.selectList(
                    new LambdaQueryWrapper<CloseLogEntity>()
                            .eq(CloseLogEntity::getPeriod, period)
                            .eq(CloseLogEntity::getDeleted, 0)
                            .orderByDesc(CloseLogEntity::getId));
            List<Map<String, Object>> result = new ArrayList<>(logs.size());
            for (CloseLogEntity c : logs) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", c.getId());
                row.put("period", c.getPeriod());
                row.put("action", c.getAction());
                row.put("operatorId", c.getOperatorId());
                row.put("result", c.getResult());
                row.put("detail", c.getDetail());
                row.put("durationMs", c.getDurationMs());
                row.put("createdAt", c.getCreatedAt());
                result.add(row);
            }
            return result;
        } catch (Exception e) {
            log.warn("查询结账日志失败 period={}, 返回空列表: {}", period, e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    @Transactional
    public void batchReviewPost(List<Long> voucherIds, Long userId) {
        // P84：一键人工审核记账。四步状态链 DRAFT→SUBMITTED→AUDITED→POSTED 在同一事务内完成，
        // 任一步失败整体回滚，不留"部分已审核未记账"的中间态。
        // 嵌套调用 VoucherService 的批量方法复用其状态机校验与期间锁检查；
        // batchSubmit/batchAudit/batchPost 均为 @Transactional(REQUIRED)，加入本方法事务。
        if (voucherIds == null || voucherIds.isEmpty()) {
            throw BusinessException.badRequest("未选择凭证");
        }
        List<Long> ids = voucherIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            throw BusinessException.badRequest("未选择有效凭证");
        }
        // 预校验：所有凭证必须处于 DRAFT，避免中途失败导致前几张已推进状态
        for (Long id : ids) {
            VoucherEntity e = voucherMapper.selectById(id);
            if (e == null || e.getDeleted() != 0) {
                throw BusinessException.notFound("凭证不存在: " + id);
            }
            if (!"DRAFT".equals(e.getStatus())) {
                throw BusinessException.badRequest(
                        "凭证非草稿状态, 无法一键审核记账: id=" + id + ", status=" + e.getStatus());
            }
        }
        voucherService.batchSubmit(ids, userId);
        voucherService.batchAudit(ids, userId);
        voucherService.batchPost(ids, userId);
        log.info("P84 一键人工审核记账完成: ids={}, userId={}", ids, userId);
    }

    /**
     * P85：追加一条结账日志。异常时只告警不抛出——日志记录是旁路，绝不影响结账业务结果。
     */
    private void appendCloseLog(String period, String action, Long userId,
                                boolean success, String detail, long durationMs) {
        try {
            CloseLogEntity entity = new CloseLogEntity();
            entity.setPeriod(period);
            entity.setAction(action);
            entity.setOperatorId(userId);
            entity.setResult(success ? "success" : "fail");
            entity.setDetail(detail);
            entity.setDurationMs(durationMs);
            entity.setDeleted(0);
            closeLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("P85 结账日志写入失败 period={} action={}: {}", period, action, e.getMessage());
        }
    }

    private PeriodEntity findPeriod(String period) {
        PeriodEntity p = periodService.lambdaQuery()
                .eq(PeriodEntity::getPeriodCode, period)
                .one();
        if (p == null) {
            throw BusinessException.notFound("会计期间不存在: " + period);
        }
        return p;
    }

    private void validateCloseOrder(String period) {
        findPeriod(period);
        String startPeriod = resolveStartPeriod();
        if (period.equals(startPeriod)) {
            return;
        }
        String prev = shiftPeriod(period, -1);
        PeriodEntity prevEntity = periodService.getByPeriodCode(prev);
        if (prevEntity == null) {
            if (startPeriod != null && period.compareTo(startPeriod) > 0) {
                throw BusinessException.badRequest(
                        "上一会计期间 " + prev + " 不存在, 请先初始化期间");
            }
            return;
        }
        if (!"closed".equals(prevEntity.getStatus())) {
            throw BusinessException.badRequest(
                    "上一会计期间 " + prev + " 尚未结账, 请先完成上期结账后再结 " + period);
        }
    }

    private void validateReopenOrder(String period) {
        String next = shiftPeriod(period, 1);
        PeriodEntity nextEntity = periodService.getByPeriodCode(next);
        if (nextEntity != null && "closed".equals(nextEntity.getStatus())) {
            throw BusinessException.badRequest(
                    "下一会计期间 " + next + " 已结账, 请先反结账 " + next + " 后再反结 " + period);
        }
    }

    private String resolveStartPeriod() {
        Long enterpriseId = EnterpriseContextHolder.get();
        if (enterpriseId == null) {
            return null;
        }
        EnterpriseEntity enterprise = enterpriseMapper.selectById(enterpriseId);
        return enterprise == null ? null : enterprise.getStartPeriod();
    }

    /** YYYYMM 月历推算，delta=-1 上一期、+1 下一期，自动跨年回绕。 */
    private static String shiftPeriod(String period, int delta) {
        int year = Integer.parseInt(period.substring(0, 4));
        int month = Integer.parseInt(period.substring(4, 6));
        int total = year * 12 + (month - 1) + delta;
        int newYear = Math.floorDiv(total, 12);
        int newMonth = Math.floorMod(total, 12) + 1;
        return String.format("%04d%02d", newYear, newMonth);
    }
}
