package com.huicai.sme.budget.service;

import com.huicai.base.voucher.dto.BudgetWarningDTO;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.service.VoucherBudgetGuard;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.budget.mapper.BudgetEntryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.hutool.core.util.StrUtil;

/**
 * P142 / REQ-2026-156（D-1~D-7 全 A 拍板）：凭证过账预算强制检查的实现.
 *
 * <h2>架构裁定</h2>
 * 接口 {@code VoucherBudgetGuard} 定义在 {@code base.voucher}（凭证过账点所在层），
 * 本实现在 {@code sme.budget}——{@code base → sme} 依赖方向全仓 0 先例，
 * 用接口倒装让 Spring 注入完成装配，不破坏「基座不依赖业务层」纪律。
 *
 * <h2>事务语义（D-1A / D-5A）</h2>
 * 调用方（{@code VoucherServiceImpl} post/unpost/batchPost）均为
 * {@code @Transactional}：本类方法**不加** @Transactional，以 REQUIRED
 * 加入调用方事务——行锁（{@code findBySubjectAndPeriodForUpdate}）与
 * {@code used_amount} 回写同事务原子提交，「先查后写」窗口被行锁封死；
 * BLOCK 抛异常 ⇒ 整个过账事务回滚（凭证不落 POSTED）。
 *
 * <p>三态语义与 {@code BudgetServiceImpl.checkBudget} 严格一致
 * （BLOCK 全拦 / APPROVE 超额→REQUIRE_APPROVE 放行 / WARN>80% 提示恒放行 /
 * 无配置 NONE 静默），但检查走**行锁**版本查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class BudgetEnforcementServiceImpl implements VoucherBudgetGuard {

    private final BudgetEntryMapper budgetEntryMapper;
    private final VoucherEntryMapper voucherEntryMapper;

    @Override
    public List<BudgetWarningDTO> enforcePost(VoucherEntity voucher, List<VoucherEntryEntity> entries) {
        // D-4A 红冲对冲：红字凭证不是新增支出——回减**原凭证**对应科目发生额，
        // 不对红冲凭证自身借贷做预算检查（它的借方=原贷方科目，语义是收入对冲）。
        if (voucher.getReversedFrom() != null) {
            adjustOriginalUsage(voucher, true);
            log.info("P142 红冲过账回减原凭证使用额: voucherId={}, reversedFrom={}, period={}",
                    voucher.getId(), voucher.getReversedFrom(), voucher.getPeriod());
            return Collections.emptyList();
        }

        Map<Long, BigDecimal> debits = aggregateDebits(entries);
        List<BudgetWarningDTO> warnings = new ArrayList<>();

        for (Map.Entry<Long, BigDecimal> e : debits.entrySet()) {
            Long subjectId = e.getKey();
            // D-5A 行锁：锁到条目行再算+回写，锁随外层事务提交释放
            List<Map<String, Object>> rows = budgetEntryMapper
                    .findBySubjectAndPeriodForUpdate(subjectId, voucher.getPeriod());
            if (rows.isEmpty()) {
                continue; // NONE：无配置不拦截（B5）
            }
            Map<String, Object> row = rows.get(0);
            BigDecimal budget = toBigDecimal(row.get("amount"));
            BigDecimal used = toBigDecimal(row.get("usedAmount"));
            BigDecimal applied = e.getValue();
            BigDecimal newUsed = used.add(applied);
            BigDecimal remaining = budget.subtract(newUsed);
            BigDecimal ratio = budget.compareTo(BigDecimal.ZERO) > 0
                    ? newUsed.multiply(BigDecimal.valueOf(100)).divide(budget, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            String subjectCode = row.get("subjectCode") == null
                    ? String.valueOf(subjectId) : row.get("subjectCode").toString();

            String controlType = row.get("controlType") == null
                    ? null : row.get("controlType").toString();
            if (StrUtil.isBlank(controlType)) {
                controlType = "WARN"; // 与 checkBudget 同兜底：历史 null 行按 WARN
            }

            switch (controlType) {
                case "BLOCK":
                    if (newUsed.compareTo(budget) > 0) {
                        // B3：fail-loud 拒绝过账（外层事务回滚，used_amount 不变）
                        log.warn("P142 预算 BLOCK 拒绝过账: voucherId={}, subject={}/{}, "
                                        + "budget={}, used={}, applied={}, newUsed={}",
                                voucher.getId(), subjectCode, subjectId, budget, used, applied, newUsed);
                        throw BusinessException.badRequest(String.format(
                                "预算不足拒绝过账：科目[%s]预算 %.2f，已用 %.2f，本笔发生 %.2f，"
                                        + "将超支 %.2f（剩余额度 %.2f）",
                                subjectCode, budget, used, applied,
                                newUsed.subtract(budget), remaining.max(BigDecimal.ZERO)));
                    }
                    budgetEntryMapper.addUsedAmount(entryId(row), applied);
                    break;
                case "APPROVE":
                    if (newUsed.compareTo(budget) > 0) {
                        // B4（D-3A）：放行 + REQUIRE_APPROVE 警告，人工事后处置
                        warnings.add(build(row, subjectId, controlType, "REQUIRE_APPROVE",
                                budget, used, newUsed, remaining, ratio));
                        log.info("P142 预算 APPROVE 态超额放行: voucherId={}, subject={}, newUsed={}, budget={}",
                                voucher.getId(), subjectCode, newUsed, budget);
                    }
                    budgetEntryMapper.addUsedAmount(entryId(row), applied);
                    break;
                case "WARN":
                default:
                    if (ratio.compareTo(BigDecimal.valueOf(80)) > 0) {
                        // B1/B2：WARN 提示（D-7A 由过账响应体携带），恒放行
                        warnings.add(build(row, subjectId, controlType, "WARN",
                                budget, used, newUsed, remaining, ratio));
                        log.info("P142 预算 WARN 提示: voucherId={}, subject={}, usageRatio={}%",
                                voucher.getId(), subjectCode, ratio);
                    }
                    budgetEntryMapper.addUsedAmount(entryId(row), applied);
                    break;
            }
        }
        return warnings;
    }

    @Override
    public void releasePost(VoucherEntity voucher, List<VoucherEntryEntity> entries) {
        // 反过账对称回减。红冲凭证的反过账是对「红冲回减」的反悔 ⇒ 对原凭证**回加**。
        if (voucher.getReversedFrom() != null) {
            adjustOriginalUsage(voucher, false);
            log.info("P142 红冲反过账回加原凭证使用额: voucherId={}, reversedFrom={}",
                    voucher.getId(), voucher.getReversedFrom());
            return;
        }
        Map<Long, BigDecimal> debits = aggregateDebits(entries);
        for (Map.Entry<Long, BigDecimal> e : debits.entrySet()) {
            List<Map<String, Object>> rows = budgetEntryMapper
                    .findBySubjectAndPeriodForUpdate(e.getKey(), voucher.getPeriod());
            if (rows.isEmpty()) {
                continue;
            }
            // used_amount 可为负：无 CHECK 下限，回减语义允许（D-4 注释即此意）
            budgetEntryMapper.addUsedAmount(entryId(rows.get(0)), e.getValue().negate());
        }
        log.info("P142 反过账预算使用额对称回减: voucherId={}, period={}, subjects={}",
                voucher.getId(), voucher.getPeriod(), debits.keySet());
    }

    /**
     * 红冲对冲分支：对**原凭证**（reversedFrom 指向）的借方聚合做回减/回加。
     * 取数用 {@code VoucherEntryMapper.selectByVoucherId}（base.voucher 反向提供，
     * 依赖方向接口倒装裁定允许的既有 base 内 mapper）。
     */
    private void adjustOriginalUsage(VoucherEntity reversal, boolean subtract) {
        List<VoucherEntryEntity> originalEntries =
                voucherEntryMapper.selectByVoucherId(reversal.getReversedFrom());
        Map<Long, BigDecimal> debits = aggregateDebits(originalEntries);
        for (Map.Entry<Long, BigDecimal> e : debits.entrySet()) {
            List<Map<String, Object>> rows = budgetEntryMapper
                    .findBySubjectAndPeriodForUpdate(e.getKey(), reversal.getPeriod());
            if (rows.isEmpty()) {
                continue;
            }
            budgetEntryMapper.addUsedAmount(entryId(rows.get(0)),
                    subtract ? e.getValue().negate() : e.getValue());
        }
    }

    /** 按科目聚合借方发生额（D-2A：只控借方——预算=支出上限，贷方不查）。 */
    private Map<Long, BigDecimal> aggregateDebits(List<VoucherEntryEntity> entries) {
        Map<Long, BigDecimal> debits = new LinkedHashMap<>();
        if (entries == null) {
            return debits;
        }
        for (VoucherEntryEntity entry : entries) {
            if (entry.getSubjectId() == null || entry.getDebit() == null) {
                continue;
            }
            if (entry.getDebit().compareTo(BigDecimal.ZERO) <= 0) {
                continue; // 借方为 0/negative 的贷方分录不控制
            }
            debits.merge(entry.getSubjectId(), entry.getDebit(), BigDecimal::add);
        }
        return debits;
    }

    private Long entryId(Map<String, Object> row) {
        Object v = row.get("id");
        return v == null ? null : ((Number) v).longValue();
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(o.toString());
    }

    private BudgetWarningDTO build(Map<String, Object> row, Long subjectId, String controlType,
                                   String action, BigDecimal budget, BigDecimal used,
                                   BigDecimal newUsed, BigDecimal remaining, BigDecimal ratio) {
        String subjectCode = row.get("subjectCode") == null
                ? String.valueOf(subjectId) : row.get("subjectCode").toString();
        String message = "WARN".equals(action)
                ? String.format("科目[%s]预算使用率已达 %s%%（预算 %.2f / 将用 %.2f）", subjectCode, ratio, budget, newUsed)
                : String.format("科目[%s]预算超支 %s%%，已放行待审批（预算 %.2f / 将用 %.2f）",
                        subjectCode, ratio, budget, newUsed);
        return new BudgetWarningDTO(subjectId, subjectCode, controlType, action,
                budget, used, newUsed, remaining, ratio, message);
    }
}
