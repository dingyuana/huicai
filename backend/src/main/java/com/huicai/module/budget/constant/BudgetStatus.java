package com.huicai.module.budget.constant;

/**
 * 预算模块状态常量.
 * <p>
 * 使用 String 常量而非 Enum，保持与数据库 VARCHAR 兼容、与 MyBatis-Plus 直接字段赋值兼容。
 * </p>
 */
public final class BudgetStatus {

    private BudgetStatus() {}

    // ==================== 预算状态 ====================

    /** 草稿 */
    public static final String BUDGET_DRAFT = "DRAFT";

    /** 已提交 */
    public static final String BUDGET_SUBMITTED = "SUBMITTED";

    /** 已批准 */
    public static final String BUDGET_APPROVED = "APPROVED";

    /** 执行中（已激活） */
    public static final String BUDGET_ACTIVE = "ACTIVE";

    /** 已关闭 */
    public static final String BUDGET_CLOSED = "CLOSED";

    /** 已驳回 */
    public static final String BUDGET_REJECTED = "REJECTED";

    /** 已冻结 */
    public static final String BUDGET_FROZEN = "FROZEN";

    // ==================== 预算调整状态 ====================

    /** 待审批 */
    public static final String ADJUSTMENT_PENDING = "PENDING";

    /** 已批准 */
    public static final String ADJUSTMENT_APPROVED = "APPROVED";

    /** 已执行 */
    public static final String ADJUSTMENT_EXECUTED = "EXECUTED";

    // ==================== 预算检查方法 ====================

    public static boolean isBudgetDraft(String status) {
        return BUDGET_DRAFT.equals(status);
    }

    public static boolean isBudgetSubmitted(String status) {
        return BUDGET_SUBMITTED.equals(status);
    }

    public static boolean isBudgetApproved(String status) {
        return BUDGET_APPROVED.equals(status);
    }

    public static boolean isBudgetActive(String status) {
        return BUDGET_ACTIVE.equals(status);
    }

    public static boolean isBudgetClosed(String status) {
        return BUDGET_CLOSED.equals(status);
    }

    public static boolean isBudgetRejected(String status) {
        return BUDGET_REJECTED.equals(status);
    }

    public static boolean isBudgetFrozen(String status) {
        return BUDGET_FROZEN.equals(status);
    }

    public static boolean isBudgetModifiable(String status) {
        return BUDGET_DRAFT.equals(status);
    }

    public static boolean isBudgetSubmittable(String status) {
        return BUDGET_DRAFT.equals(status);
    }

    public static boolean isBudgetApprovable(String status) {
        return BUDGET_SUBMITTED.equals(status);
    }

    public static boolean isBudgetFreezable(String status) {
        return BUDGET_APPROVED.equals(status);
    }

    // ==================== 预算调整检查方法 ====================

    public static boolean isAdjustmentPending(String status) {
        return ADJUSTMENT_PENDING.equals(status);
    }

    public static boolean isAdjustmentApproved(String status) {
        return ADJUSTMENT_APPROVED.equals(status);
    }

    public static boolean isAdjustmentExecuted(String status) {
        return ADJUSTMENT_EXECUTED.equals(status);
    }

    public static boolean isAdjustmentModifiable(String status) {
        return ADJUSTMENT_PENDING.equals(status);
    }

    public static boolean isAdjustmentApprovable(String status) {
        return ADJUSTMENT_PENDING.equals(status);
    }

    public static boolean isAdjustmentExecutable(String status) {
        return ADJUSTMENT_APPROVED.equals(status);
    }
}