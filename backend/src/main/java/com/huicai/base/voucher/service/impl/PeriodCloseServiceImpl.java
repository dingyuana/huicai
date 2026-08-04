package com.huicai.base.voucher.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.voucher.service.PeriodCloseService;
import com.huicai.base.voucher.constant.VoucherType;
import com.huicai.base.balance.service.SubjectBalanceService;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.system.service.PeriodService;
import com.huicai.base.system.service.SubjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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

        // 本年利润科目（4103）
        Subject profitSubject = subjectMapper.selectOne(
                new LambdaQueryWrapper<Subject>()
                        .eq(Subject::getCode, "4103")
                        .eq(Subject::getDeleted, 0)
                        .last("LIMIT 1"));
        if (profitSubject == null) {
            throw BusinessException.badRequest("未配置本年利润科目(4103), 无法生成结转凭证");
        }

        // 汇总本期间已记账凭证的科目借贷发生额
        List<VoucherEntryEntity> allEntries = voucherEntryMapper.selectList(null);
        Map<Long, BigDecimal[]> agg = new HashMap<>();
        for (VoucherEntryEntity e : allEntries) {
            VoucherEntity v = voucherMapper.selectById(e.getVoucherId());
            if (v == null || !"POSTED".equals(v.getStatus())) continue;
            if (!period.equals(v.getPeriod())) continue;
            if (Boolean.TRUE.equals(v.getDeleted())) continue;
            BigDecimal d = e.getDebit() == null ? BigDecimal.ZERO : e.getDebit();
            BigDecimal c = e.getCredit() == null ? BigDecimal.ZERO : e.getCredit();
            BigDecimal[] arr = agg.computeIfAbsent(e.getSubjectId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            arr[0] = arr[0].add(d);
            arr[1] = arr[1].add(c);
        }
        if (agg.isEmpty()) {
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
        for (Map.Entry<Long, BigDecimal[]> entry : agg.entrySet()) {
            Long subjectId = entry.getKey();
            if (subjectId.equals(profitSubject.getId())) continue;
            BigDecimal debit = entry.getValue()[0];
            BigDecimal credit = entry.getValue()[1];
            BigDecimal net = debit.subtract(credit);

            Subject subject = subjectService.getById(subjectId);
            if (subject == null) continue;
            // 仅处理损益类科目(6xx): 收入(credit)与费用(debit)
            if (subject.getCode() == null || subject.getCode().length() < 3
                    || !subject.getCode().startsWith("6")) continue;

            VoucherEntryEntity line;
            if ("credit".equals(subject.getDirection())) {
                // 收入类: 贷方余额反向结转 -> 借收入科目 / 贷本年利润
                if (net.signum() >= 0) continue; // 收入科目净额为借(异常)时跳过
                BigDecimal amount = net.abs();
                line = entryOf(voucher.getId(), subjectId, amount, BigDecimal.ZERO, sort++,
                        "结转收入 " + subject.getName() + " 至本年利润");
                voucherEntryMapper.insert(line);
                line = entryOf(voucher.getId(), profitSubject.getId(), BigDecimal.ZERO, amount, sort++,
                        "收入结转 " + subject.getName());
                voucherEntryMapper.insert(line);
                totalD = totalD.add(amount);
                totalC = totalC.add(amount);
            } else {
                // 费用类: 借方余额反向结转 -> 借本年利润 / 贷费用科目
                if (net.signum() <= 0) continue;
                BigDecimal amount = net;
                line = entryOf(voucher.getId(), profitSubject.getId(), amount, BigDecimal.ZERO, sort++,
                        "结转费用 " + subject.getName() + " 至本年利润");
                voucherEntryMapper.insert(line);
                line = entryOf(voucher.getId(), subjectId, BigDecimal.ZERO, amount, sort++,
                        "费用结转 " + subject.getName());
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
        Map<String, Object> check = checkBeforeClose(period);
        if (!Boolean.TRUE.equals(check.get("passed"))) {
            throw BusinessException.badRequest("结账检查未通过: " + check.get("issues"));
        }

        PeriodEntity periodEntity = findPeriod(period);
        periodEntity.setStatus("closed");
        periodEntity.setUpdatedBy(userId);
        periodEntity.setUpdatedAt(LocalDateTime.now());
        periodService.updateById(periodEntity);

        log.info("期间已结账: period={}, userId={}", period, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reopenPeriod(String period, Long userId) {
        PeriodEntity periodEntity = findPeriod(period);
        if (!"closed".equals(periodEntity.getStatus())) {
            throw BusinessException.badRequest("仅已结账期间可反结账");
        }
        periodEntity.setStatus("open");
        periodEntity.setUpdatedBy(userId);
        periodEntity.setUpdatedAt(LocalDateTime.now());
        periodService.updateById(periodEntity);
        log.info("期间已反结账: period={}, userId={}", period, userId);
    }

    @Override
    public List<Map<String, Object>> listCloseLog(String period) {
        return new ArrayList<>();
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
}
