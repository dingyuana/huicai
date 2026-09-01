package com.huicai.base.balance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.huicai.common.annotation.Auditable;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import com.huicai.agency.tenant.entity.EnterpriseEntity;
import com.huicai.agency.tenant.mapper.EnterpriseMapper;
import com.huicai.base.balance.entity.SubjectBalanceEntity;
import com.huicai.base.balance.dto.SubjectBalanceVO;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.balance.mapper.SubjectBalanceMapper;
import com.huicai.base.balance.service.SubjectBalanceService;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.service.PeriodService;
import com.huicai.base.system.service.SubjectService;
import com.huicai.base.system.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 科目余额更新服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubjectBalanceServiceImpl implements SubjectBalanceService {

    private final SubjectBalanceMapper subjectBalanceMapper;
    private final SubjectService subjectService;
    private final PeriodService periodService;
    private final VoucherMapper voucherMapper;
    private final EnterpriseMapper enterpriseMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void updateBalanceOnPost(VoucherEntity voucher, List<VoucherEntryEntity> entries) {
        String period = voucher.getPeriod();
        int year = Integer.parseInt(period.substring(0, 4));

        for (VoucherEntryEntity entry : entries) {
            Subject subject = subjectService.getById(entry.getSubjectId());
            if (subject == null) {
                throw BusinessException.notFound("科目不存在: " + entry.getSubjectId());
            }

            if (!Boolean.TRUE.equals(subject.getIsLeaf())) {
                continue;
            }

            SubjectBalanceEntity balance = findOrCreate(subject.getId(), period, year);

            BigDecimal newDebitTotal = balance.getDebitTotal().add(entry.getDebit());
            BigDecimal newCreditTotal = balance.getCreditTotal().add(entry.getCredit());
            balance.setDebitTotal(newDebitTotal);
            balance.setCreditTotal(newCreditTotal);

            BigDecimal endBalance;
            if ("debit".equals(subject.getDirection())) {
                endBalance = balance.getBeginBalance().add(newDebitTotal).subtract(newCreditTotal);
            } else {
                endBalance = balance.getBeginBalance().add(newCreditTotal).subtract(newDebitTotal);
            }
            balance.setEndBalance(endBalance);

            subjectBalanceMapper.updateById(balance);
            log.info("更新科目余额: subjectId={}, period={}, debitTotal={}, creditTotal={}, endBalance={}",
                    subject.getId(), period, newDebitTotal, newCreditTotal, endBalance);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Auditable(operation = "期初建账", module = "SUBJECT_BALANCE")
    public void initOpeningBalances(String period, Map<Long, BigDecimal> balances) {
        initOpeningBalances(period, null, balances);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Auditable(operation = "期初建账", module = "SUBJECT_BALANCE")
    public void initOpeningBalances(String period, LocalDateTime openedAt, Map<Long, BigDecimal> balances) {
        if (period == null || period.length() != 6) {
            throw BusinessException.badRequest("会计期间格式错误, 应为 YYYYMM");
        }
        if (balances == null) {
            throw BusinessException.badRequest("期初余额数据不能为空");
        }

        PeriodEntity periodEntity = periodService.getByPeriodCode(period);
        if (periodEntity == null) {
            throw BusinessException.badRequest("会计期间不存在: " + period + ", 请先在期间管理中创建");
        }
        if (!"open".equals(periodEntity.getStatus())) {
            throw BusinessException.badRequest("会计期间已" + periodEntity.getStatus() + ", 不可录入期初: " + period);
        }
        if ("locked".equals(periodEntity.getOpeningStatus())) {
            throw BusinessException.conflict("期间 " + period + " 期初已锁定, 不可重新录入");
        }

        int year = Integer.parseInt(period.substring(0, 4));

        LambdaQueryWrapper<SubjectBalanceEntity> existsWrapper = new LambdaQueryWrapper<>();
        existsWrapper.eq(SubjectBalanceEntity::getPeriod, period);
        Long existing = subjectBalanceMapper.selectCount(existsWrapper);
        if (existing > 0) {
            throw BusinessException.conflict("期间 " + period + " 已存在余额数据, 请先清空重录");
        }

        LocalDateTime openAt = openedAt != null ? openedAt : LocalDateTime.now();
        Long operatorId = resolveCurrentUserId();
        String operatorName = resolveCurrentUsername();

        // 空 balances 表示确认期初全为 0（支持零余额企业），仅标记 entered 不插入数据行
        if (balances.isEmpty()) {
            periodService.markOpeningEntered(period, openAt, operatorId, operatorName);
            backfillEnterpriseStartPeriod(period);
            log.info("期初建账（零余额确认）: period={}, openedAt={}, operator={}", period, openAt, operatorName);
            return;
        }

        BigDecimal totalDebitSide = BigDecimal.ZERO;
        BigDecimal totalCreditSide = BigDecimal.ZERO;
        List<SubjectBalanceEntity> batchList = new ArrayList<>();

        for (Map.Entry<Long, BigDecimal> entry : balances.entrySet()) {
            Long subjectId = entry.getKey();
            BigDecimal opening = entry.getValue() == null ? BigDecimal.ZERO : entry.getValue();

            Subject subject = subjectService.getById(subjectId);
            if (subject == null) {
                throw BusinessException.notFound("科目不存在: " + subjectId);
            }
            if (!Boolean.TRUE.equals(subject.getIsLeaf())) {
                throw BusinessException.badRequest("非末级科目不允许录入期初余额: " + subject.getCode());
            }

            if ("debit".equals(subject.getDirection())) {
                totalDebitSide = totalDebitSide.add(opening);
            } else {
                totalCreditSide = totalCreditSide.add(opening);
            }

            SubjectBalanceEntity balance = new SubjectBalanceEntity();
            balance.setSubjectId(subjectId);
            balance.setYear(year);
            balance.setPeriod(period);
            balance.setBeginBalance(opening);
            balance.setDebitTotal(BigDecimal.ZERO);
            balance.setCreditTotal(BigDecimal.ZERO);
            balance.setEndBalance(opening);
            batchList.add(balance);
        }

        if (totalDebitSide.compareTo(totalCreditSide) != 0) {
            throw BusinessException.badRequest(
                    "期初试算不平衡: 借方合计=" + totalDebitSide + ", 贷方合计=" + totalCreditSide);
        }

        for (SubjectBalanceEntity balance : batchList) {
            subjectBalanceMapper.insert(balance);
        }
        periodService.markOpeningEntered(period, openAt, operatorId, operatorName);
        backfillEnterpriseStartPeriod(period);
        log.info("期初建账完成: period={}, 科目数={}, 借方合计={}, 贷方合计={}, openedAt={}, operator={}",
                period, batchList.size(), totalDebitSide, totalCreditSide, openAt, operatorName);
    }

    private Long resolveCurrentUserId() {
        try {
            return SecurityUtils.getCurrentUserId();
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveCurrentUsername() {
        try {
            return SecurityUtils.getCurrentUsername();
        } catch (Exception e) {
            return "anonymous";
        }
    }

    private void backfillEnterpriseStartPeriod(String period) {
        Long enterpriseId = EnterpriseContextHolder.get();
        if (enterpriseId == null) {
            return;
        }
        EnterpriseEntity enterprise = enterpriseMapper.selectById(enterpriseId);
        if (enterprise == null || enterprise.getStartPeriod() != null) {
            return;
        }
        EnterpriseEntity update = new EnterpriseEntity();
        update.setId(enterpriseId);
        update.setStartPeriod(period);
        enterpriseMapper.updateById(update);
        log.info("回填企业建账期间: enterpriseId={}, startPeriod={}", enterpriseId, period);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Auditable(operation = "锁定期初", module = "SUBJECT_BALANCE")
    public void lockOpeningBalances(String period) {
        if (period == null || period.length() != 6) {
            throw BusinessException.badRequest("会计期间格式错误, 应为 YYYYMM");
        }
        PeriodEntity periodEntity = periodService.getByPeriodCode(period);
        if (periodEntity == null) {
            throw BusinessException.badRequest("会计期间不存在: " + period);
        }
        if ("closed".equals(periodEntity.getStatus()) || "locked".equals(periodEntity.getStatus())) {
            throw BusinessException.badRequest("会计期间已" + periodEntity.getStatus() + ", 不可锁定期初: " + period);
        }
        if (!"entered".equals(periodEntity.getOpeningStatus())) {
            throw BusinessException.badRequest("期间 " + period + " 尚未完成期初建账, 不可锁定");
        }

        // 锁定前必须试算平衡
        Map<String, Object> trial = checkTrialBalance(period);
        if (!Boolean.TRUE.equals(trial.get("beginBalanced"))) {
            throw BusinessException.badRequest("期初借贷不平衡, 不可锁定: 借方="
                    + trial.get("totalBeginDebit") + ", 贷方=" + trial.get("totalBeginCredit"));
        }

        periodService.setOpeningStatus(period, "locked");
        log.info("锁定期初完成: period={}", period);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Auditable(operation = "解锁期初", module = "SUBJECT_BALANCE")
    public void unlockOpeningBalances(String period) {
        if (period == null || period.length() != 6) {
            throw BusinessException.badRequest("会计期间格式错误, 应为 YYYYMM");
        }
        PeriodEntity periodEntity = periodService.getByPeriodCode(period);
        if (periodEntity == null) {
            throw BusinessException.badRequest("会计期间不存在: " + period);
        }
        // 锁定 = 终态：禁止解锁（P59）。修正期初只能通过红冲凭证方式
        if ("locked".equals(periodEntity.getOpeningStatus())) {
            throw BusinessException.badRequest("期间 " + period + " 期初已锁定, 锁定后不可解锁/清空/重录, 如需修正请通过红冲凭证方式处理");
        }
        throw BusinessException.badRequest("期间 " + period + " 期初未处于锁定状态, 无需解锁");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Auditable(operation = "清空期初", module = "SUBJECT_BALANCE")
    public void clearOpeningBalances(String period) {
        if (period == null || period.length() != 6) {
            throw BusinessException.badRequest("会计期间格式错误, 应为 YYYYMM");
        }
        PeriodEntity periodEntity = periodService.getByPeriodCode(period);
        if (periodEntity == null) {
            throw BusinessException.badRequest("会计期间不存在: " + period);
        }
        if ("locked".equals(periodEntity.getOpeningStatus())) {
            throw BusinessException.conflict("期间 " + period + " 期初已锁定, 不可清空");
        }

        // 期间已有 POSTED 凭证时不允许清空：会破坏与凭证发生额的一致性
        Long postedCount = voucherMapper.selectCount(
                new LambdaQueryWrapper<VoucherEntity>()
                        .eq(VoucherEntity::getPeriod, period)
                        .eq(VoucherEntity::getStatus, "POSTED")
                        .eq(VoucherEntity::getDeleted, 0));
        if (postedCount != null && postedCount > 0) {
            throw BusinessException.conflict("期间 " + period + " 已存在 " + postedCount
                    + " 张已过账凭证, 不允许清空期初, 请通过红冲凭证方式修正");
        }

        LambdaQueryWrapper<SubjectBalanceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubjectBalanceEntity::getPeriod, period);
        int deleted = subjectBalanceMapper.delete(wrapper);
        periodService.setOpeningStatus(period, "none");
        resetStartPeriodIfAlone(period);
        log.info("清空期初完成: period={}, 删除余额行={}", period, deleted);
    }

    private void resetStartPeriodIfAlone(String period) {
        Long enterpriseId = EnterpriseContextHolder.get();
        if (enterpriseId == null) {
            return;
        }
        EnterpriseEntity enterprise = enterpriseMapper.selectById(enterpriseId);
        if (enterprise == null || !period.equals(enterprise.getStartPeriod())) {
            return;
        }
        LambdaQueryWrapper<SubjectBalanceEntity> otherWrapper = new LambdaQueryWrapper<>();
        otherWrapper.ne(SubjectBalanceEntity::getPeriod, period);
        Long remaining = subjectBalanceMapper.selectCount(otherWrapper);
        if (remaining == null || remaining == 0) {
            // updateById 的 NOT_NULL 策略会跳过 null 字段，必须用 UpdateWrapper.set 显式置空
            // 使用 UpdateWrapper（非 Lambda 版本）避免纯 Mockito 测试中 MyBatis-Plus lambda cache 初始化失败
            enterpriseMapper.update(null, new UpdateWrapper<EnterpriseEntity>()
                    .eq("id", enterpriseId)
                    .set("start_period", null));
            log.info("清空建账期间后重置企业 start_period: enterpriseId={}", enterpriseId);
        }
    }

    @Override
    public void validateOpeningBeforePost(String period) {
        if (period == null || period.length() != 6) return;
        PeriodEntity target = periodService.getByPeriodCode(period);
        if (target == null) return; // 期间不存在时放行（避免破坏未初始化场景）
        String status = target.getOpeningStatus();

        String startPeriod = resolveStartPeriod();
        if (startPeriod == null) {
            // 存量企业（无 start_period）：仅最早期期间强制先建账，保持向后兼容
            if (status == null || !"none".equals(status)) return;
            LambdaQueryWrapper<PeriodEntity> earliestWrapper = new LambdaQueryWrapper<>();
            earliestWrapper.orderByAsc(PeriodEntity::getPeriodCode).last("LIMIT 1");
            PeriodEntity earliest = periodService.getOne(earliestWrapper);
            if (earliest != null && period.equals(earliest.getPeriodCode())) {
                throw BusinessException.badRequest("期间 " + period
                        + " 尚未完成期初建账, 请先在「期初建账」模块录入期初余额再过账凭证");
            }
            return;
        }

        int cmp = period.compareTo(startPeriod);
        if (cmp < 0) {
            throw BusinessException.badRequest("期间 " + period + " 早于企业建账期间 " + startPeriod + ", 未启用, 无法过账");
        }
        if (cmp == 0 && "none".equals(status)) {
            throw BusinessException.badRequest("期间 " + period
                    + " 尚未完成期初建账, 请先在「期初建账」模块录入期初余额再过账凭证");
        }
        // period > start_period 或 start_period 期间已建账 → 放行
    }

    private String resolveStartPeriod() {
        Long enterpriseId = EnterpriseContextHolder.get();
        if (enterpriseId == null) {
            return null;
        }
        EnterpriseEntity enterprise = enterpriseMapper.selectById(enterpriseId);
        return enterprise == null ? null : enterprise.getStartPeriod();
    }

    @Override
    public PeriodEntity getPeriodEntity(String period) {
        return periodService.getByPeriodCode(period);
    }

    @Override
    public List<SubjectBalanceEntity> queryByPeriod(String period) {
        if (period == null || period.length() != 6) {
            throw BusinessException.badRequest("会计期间格式错误, 应为 YYYYMM");
        }
        LambdaQueryWrapper<SubjectBalanceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubjectBalanceEntity::getPeriod, period)
                .orderByAsc(SubjectBalanceEntity::getSubjectId);
        return subjectBalanceMapper.selectList(wrapper);
    }

    @Override
    public List<SubjectBalanceVO> queryByPeriodWithSubject(String period) {
        List<SubjectBalanceEntity> balances = queryByPeriod(period);
        if (balances.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> subjectIds = balances.stream()
                .map(SubjectBalanceEntity::getSubjectId)
                .collect(Collectors.toSet());
        Map<Long, Subject> subjectMap = subjectService.listByIds(subjectIds).stream()
                .collect(Collectors.toMap(Subject::getId, s -> s, (a, b) -> a));

        List<SubjectBalanceVO> result = new ArrayList<>(balances.size());
        for (SubjectBalanceEntity balance : balances) {
            SubjectBalanceVO vo = new SubjectBalanceVO();
            vo.setId(balance.getId());
            vo.setSubjectId(balance.getSubjectId());
            vo.setYear(balance.getYear());
            vo.setPeriod(balance.getPeriod());
            vo.setBeginBalance(balance.getBeginBalance());
            vo.setDebitTotal(balance.getDebitTotal());
            vo.setCreditTotal(balance.getCreditTotal());
            vo.setEndBalance(balance.getEndBalance());
            vo.setCreatedAt(balance.getCreatedAt());
            vo.setUpdatedAt(balance.getUpdatedAt());

            Subject subject = subjectMap.get(balance.getSubjectId());
            if (subject != null) {
                vo.setSubjectCode(subject.getCode());
                vo.setSubjectName(subject.getName());
                vo.setDirection(subject.getDirection());
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public Map<String, Object> checkTrialBalance(String period) {
        if (period == null || period.length() != 6) {
            throw BusinessException.badRequest("会计期间格式错误, 应为 YYYYMM");
        }

        List<SubjectBalanceEntity> balances = queryByPeriod(period);

        BigDecimal totalBeginDebit = BigDecimal.ZERO;
        BigDecimal totalBeginCredit = BigDecimal.ZERO;
        BigDecimal totalDebitTotal = BigDecimal.ZERO;
        BigDecimal totalCreditTotal = BigDecimal.ZERO;
        BigDecimal totalEndDebit = BigDecimal.ZERO;
        BigDecimal totalEndCredit = BigDecimal.ZERO;

        for (SubjectBalanceEntity balance : balances) {
            Subject subject = subjectService.getById(balance.getSubjectId());
            if (subject == null) {
                continue;
            }
            if (!Boolean.TRUE.equals(subject.getIsLeaf())) {
                continue;
            }

            BigDecimal begin = balance.getBeginBalance() == null ? BigDecimal.ZERO : balance.getBeginBalance();
            BigDecimal debit = balance.getDebitTotal() == null ? BigDecimal.ZERO : balance.getDebitTotal();
            BigDecimal credit = balance.getCreditTotal() == null ? BigDecimal.ZERO : balance.getCreditTotal();
            BigDecimal end = balance.getEndBalance() == null ? BigDecimal.ZERO : balance.getEndBalance();

            if ("debit".equals(subject.getDirection())) {
                totalBeginDebit = totalBeginDebit.add(begin);
                totalDebitTotal = totalDebitTotal.add(debit);
                totalCreditTotal = totalCreditTotal.add(credit);
                if (end.compareTo(BigDecimal.ZERO) >= 0) {
                    totalEndDebit = totalEndDebit.add(end);
                } else {
                    totalEndCredit = totalEndCredit.add(end.abs());
                }
            } else {
                totalBeginCredit = totalBeginCredit.add(begin);
                totalDebitTotal = totalDebitTotal.add(debit);
                totalCreditTotal = totalCreditTotal.add(credit);
                if (end.compareTo(BigDecimal.ZERO) >= 0) {
                    totalEndCredit = totalEndCredit.add(end);
                } else {
                    totalEndDebit = totalEndDebit.add(end.abs());
                }
            }
        }

        boolean beginBalanced = totalBeginDebit.compareTo(totalBeginCredit) == 0;
        boolean movementBalanced = totalDebitTotal.compareTo(totalCreditTotal) == 0;
        boolean endBalanced = totalEndDebit.compareTo(totalEndCredit) == 0;

        // D4-修复：无余额快照（期间未过账/未建账）时返回 empty=true，区分「真平衡」与「无数据」假阳性
        boolean empty = balances.isEmpty();

        Map<String, Object> result = new HashMap<>();
        result.put("period", period);
        result.put("empty", empty);
        if (empty) {
            result.put("emptyMessage", "该期间无余额数据，可能是尚未过账或未建账，无法判断借贷平衡");
        }
        result.put("beginBalanced", beginBalanced);
        result.put("movementBalanced", movementBalanced);
        result.put("endBalanced", endBalanced);
        result.put("totalBeginDebit", totalBeginDebit);
        result.put("totalBeginCredit", totalBeginCredit);
        result.put("totalDebitTotal", totalDebitTotal);
        result.put("totalCreditTotal", totalCreditTotal);
        result.put("totalEndDebit", totalEndDebit);
        result.put("totalEndCredit", totalEndCredit);
        result.put("balanced", beginBalanced && movementBalanced && endBalanced);
        return result;
    }

    private SubjectBalanceEntity findOrCreate(Long subjectId, String period, int year) {
        LambdaQueryWrapper<SubjectBalanceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubjectBalanceEntity::getSubjectId, subjectId)
                .eq(SubjectBalanceEntity::getPeriod, period);
        SubjectBalanceEntity balance = subjectBalanceMapper.selectOne(wrapper);

        if (balance == null) {
            BigDecimal beginBalance = getPreviousEndBalance(subjectId, period);

            balance = new SubjectBalanceEntity();
            balance.setSubjectId(subjectId);
            balance.setYear(year);
            balance.setPeriod(period);
            balance.setBeginBalance(beginBalance);
            balance.setDebitTotal(BigDecimal.ZERO);
            balance.setCreditTotal(BigDecimal.ZERO);
            balance.setEndBalance(beginBalance);
            subjectBalanceMapper.insert(balance);
        }

        return balance;
    }

    private BigDecimal getPreviousEndBalance(Long subjectId, String period) {
        int year = Integer.parseInt(period.substring(0, 4));
        int month = Integer.parseInt(period.substring(4, 6));

        while (true) {
            if (month == 1) {
                year--;
                month = 12;
            } else {
                month--;
            }
            String prevPeriod = String.format("%04d%02d", year, month);

            LambdaQueryWrapper<SubjectBalanceEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SubjectBalanceEntity::getSubjectId, subjectId)
                    .eq(SubjectBalanceEntity::getPeriod, prevPeriod);
            SubjectBalanceEntity prev = subjectBalanceMapper.selectOne(wrapper);

            if (prev != null) {
                return prev.getEndBalance();
            }

            if (month == 12 && year == Integer.parseInt(period.substring(0, 4)) - 1) {
                return BigDecimal.ZERO;
            }
        }
    }
}
