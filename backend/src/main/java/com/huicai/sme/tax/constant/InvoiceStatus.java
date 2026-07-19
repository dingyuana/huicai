package com.huicai.sme.tax.constant;

/**
 * 发票模块统一状态常量.
 * 使用 String 常量而非 Enum，保持与数据库 VARCHAR 兼容.
 *
 * 状态机详见 docs/需求分析书_发票与凭证状态机_V1.0.md §3.1
 * 实施 SPEC: docs/specs/P21-sales-invoice-state-machine.md
 * 与 P20 (ArapStatus) 的边界详见 P20 SPEC §10.
 *
 * 状态历史（2026-06-21 V45 迁移）：
 * - V8 旧 4 状态: DRAFT / ISSUED / VOID / RED_INK
 * - V45 新 8 状态: 7 个需求文档定义 + REVERSED（承接旧 RED_INK 数据）
 */
public final class InvoiceStatus {

    private InvoiceStatus() {}

    // ====== 导入与审核 ======
    public static final String PENDING_CONFIRM = "PENDING_CONFIRM";
    public static final String PENDING_REVIEW = "PENDING_REVIEW";
    public static final String CONFIRMED = "CONFIRMED";

    // ====== 凭证与核销 ======
    public static final String VOUCHERED = "VOUCHERED";
    public static final String FULLY_RECONCILED = "FULLY_RECONCILED";
    public static final String PARTIALLY_RECONCILED = "PARTIALLY_RECONCILED";

    // ====== 终止 ======
    public static final String VOIDED = "VOIDED";

    // ====== 冲销（2026-06-21 V45 迁移加入，承接旧 RED_INK 数据）======
    public static final String REVERSED = "REVERSED";

    // ====== 检查方法 ======
    public static boolean isPendingConfirm(String status) {
        return PENDING_CONFIRM.equals(status);
    }

    public static boolean isPendingReview(String status) {
        return PENDING_REVIEW.equals(status);
    }

    public static boolean isConfirmed(String status) {
        return CONFIRMED.equals(status);
    }

    public static boolean isVoucherable(String status) {
        return CONFIRMED.equals(status);
    }

    public static boolean isVouchered(String status) {
        return VOUCHERED.equals(status);
    }

    public static boolean isReconciled(String status) {
        return FULLY_RECONCILED.equals(status) || PARTIALLY_RECONCILED.equals(status);
    }

    public static boolean isVoidable(String status) {
        // 任何非终态都可作废（VOIDED/REVERSED/FULLY_RECONCILED 不可作废）
        return !VOIDED.equals(status)
            && !REVERSED.equals(status)
            && !FULLY_RECONCILED.equals(status);
    }

    public static boolean isModifiable(String status) {
        // 仅 PENDING_CONFIRM / PENDING_REVIEW 可修改
        return PENDING_CONFIRM.equals(status) || PENDING_REVIEW.equals(status);
    }

    public static boolean isReversed(String status) {
        return REVERSED.equals(status);
    }

    public static boolean isTerminal(String status) {
        // 终态：VOIDED / REVERSED / FULLY_RECONCILED
        return VOIDED.equals(status)
            || REVERSED.equals(status)
            || FULLY_RECONCILED.equals(status);
    }

    /** 是否可红冲（CONFIRMED / VOUCHERED / PARTIALLY_RECONCILED） */
    public static boolean isReversible(String status) {
        return CONFIRMED.equals(status)
            || VOUCHERED.equals(status)
            || PARTIALLY_RECONCILED.equals(status);
    }
}