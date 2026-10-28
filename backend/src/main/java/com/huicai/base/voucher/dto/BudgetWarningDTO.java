package com.huicai.base.voucher.dto;

import java.math.BigDecimal;

/**
 * P142 预算执行联动——过账钩子返回的预算提示条目（D-7A：过账响应体携带）.
 *
 * <p>定义放 {@code base.voucher.dto} 的原因：{@code VoucherBudgetGuard} 接口
 * 属 {@code base} 层（ {@code base → sme} 依赖方向全仓 0 先例，禁止反向依赖），
 * 其签名上的类型必须停留在 base。
 *
 * <p>出参面铁律 #13：本类以 {@code DTO} 结尾，不触发零容忍守卫。
 */
public record BudgetWarningDTO(
        Long subjectId,
        String subjectCode,
        String controlType,
        /** WARN / REQUIRE_APPROVE；BLOCK 走异常不出现在本条目 */
        String action,
        BigDecimal budget,
        BigDecimal used,
        BigDecimal newUsed,
        BigDecimal remaining,
        BigDecimal usageRatio,
        String message
) {
}
