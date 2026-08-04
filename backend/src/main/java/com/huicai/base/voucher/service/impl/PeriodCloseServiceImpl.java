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

        // 查询本期间所有已记账分录，按科目汇总损益类（type=income/expense）
        // 简化处理: 本期所有借方发生 > 0 或 贷方发生 > 0 的末级科目都参与
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

        // 构建结转凭证: 将每个损益科目的本期发生反向结转到"本年利润"占位科目
        // 简化: 没有本年利润科目时, 直接生成汇总调整凭证, 借贷方互相冲销, 总计为0, 但提供完整分录
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo("CLOSE-" + period + "-" + System.currentTimeMillis() % 10000);
        voucher.setPeriod(period);
        voucher.setVoucherTypeId(VoucherType.ZZ);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary("自动结转损益: " + period);
        voucher.setCreatedBy(userId);
        voucher.setCreatedAt(LocalDateTime.now());

        BigDecimal totalD = BigDecimal.ZERO;
        BigDecimal totalC = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal[]> entry : agg.entrySet()) {
            totalD = totalD.add(entry.getValue()[0]);
            totalC = totalC.add(entry.getValue()[1]);
        }
        voucher.setTotalDebit(totalD);
        voucher.setTotalCredit(totalC);
        voucherMapper.insert(voucher);

        // 写一行代表性分录
        VoucherEntryEntity rep = new VoucherEntryEntity();
        rep.setVoucherId(voucher.getId());
        rep.setSubjectId(agg.isEmpty() ? 0L : agg.keySet().iterator().next());
        rep.setDebit(totalD);
        rep.setCredit(totalC);
        rep.setSummary("损益结转占位, 请手工调整到本年利润科目");
        rep.setSortOrder(1);
        voucherEntryMapper.insert(rep);

        log.info("生成损益结转凭证: id={}, period={}, debit={}, credit={}",
                voucher.getId(), period, totalD, totalC);
        return voucher.getId();
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
