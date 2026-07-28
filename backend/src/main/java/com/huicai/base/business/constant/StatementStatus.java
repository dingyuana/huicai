package com.huicai.base.business.constant;

/**
 * 银行流水对账单 reviewStatus 状态常量.
 * 使用 String 常量而非 Enum，保持与数据库 VARCHAR 兼容。
 * P23 SPEC: docs/specs/P23-bank-statement-state-machine.md
 */
public final class StatementStatus {

    private StatementStatus() {}

    // ====== 初始状态 ======
    public static final String CLASSIFIED = "classified";
    public static final String PENDING = "PENDING";

    // ====== 出纳确认 ======
    public static final String CONFIRMED = "CONFIRMED";

    // ====== 生成结果 ======
    public static final String VOUCHER_GENERATED = "voucher_generated";
    public static final String PAYMENT_CREATED = "payment_created";

    // ====== 最终核准 ======
    public static final String APPROVED = "approved";

    // ====== C类人工处理 ======
    public static final String MANUAL_PENDING = "manual_pending";

    // ====== 兼容旧状态 ======
    public static final String UNCONFIRMED = "UNCONFIRMED";
    public static final String RECLASSIFIED = "RECLASSIFIED";
    public static final String DUPLICATE = "DUPLICATE";

    // ====== 检查方法 ======

    public static boolean isReviewable(String status) {
        return status == null
                || PENDING.equals(status)
                || CLASSIFIED.equals(status)
                || MANUAL_PENDING.equals(status)
                || RECLASSIFIED.equals(status)
                || UNCONFIRMED.equals(status);
    }

    public static boolean isAuditable(String status) {
        return CONFIRMED.equals(status);
    }

    public static boolean isGeneratable(String status) {
        return CONFIRMED.equals(status) || "AUDITED".equals(status);
    }

    public static boolean isApprovable(String status) {
        return VOUCHER_GENERATED.equals(status) || PAYMENT_CREATED.equals(status);
    }

    public static boolean isLocked(String status) {
        return CONFIRMED.equals(status)
                || VOUCHER_GENERATED.equals(status)
                || PAYMENT_CREATED.equals(status)
                || APPROVED.equals(status);
    }

    public static boolean isManualPending(String status) {
        return MANUAL_PENDING.equals(status);
    }

    public static boolean isApproved(String status) {
        return APPROVED.equals(status);
    }

    public static boolean isProcessed(String status) {
        return VOUCHER_GENERATED.equals(status)
                || PAYMENT_CREATED.equals(status)
                || APPROVED.equals(status)
                || DUPLICATE.equals(status);
    }
}
