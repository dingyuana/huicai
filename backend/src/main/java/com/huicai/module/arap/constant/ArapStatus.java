package com.huicai.module.arap.constant;

/**
 * AR/AP 模块统一状态常量.
 * <p>
 * 使用 String 常量而非 Enum，保持与数据库 VARCHAR 兼容、与 MyBatis-Plus 直接字段赋值兼容。
 * 所有 AR/AP 模块的 status 字段赋值和校验必须使用此常量类，禁止 magic string。
 * </p>
 */
public final class ArapStatus {

    private ArapStatus() {}

    // ==================== 通用 ====================

    /** 草稿 */
    public static final String DRAFT = "DRAFT";
    /** 已提交（等待审批） */
    public static final String SUBMITTED = "SUBMITTED";
    /** 已确认 */
    public static final String CONFIRMED = "CONFIRMED";
    /** 已驳回 */
    public static final String REJECTED = "REJECTED";
    /** 已冲销/反核销 */
    public static final String REVERSED = "REVERSED";

    // ==================== Receivable / Payable 特有 ====================

    /** 已结清（unsettled_amount = 0） */
    public static final String SETTLED = "SETTLED";

    // ==================== Settlement 特有 ====================

    /** 已生成凭证 */
    public static final String VOUCHERED = "VOUCHERED";

    // ==================== ReconciliationLog 特有 ====================

    /** 已执行 */
    public static final String EXECUTED = "EXECUTED";
    /** 已拒绝（ReconciliationLog 特有） */
    public static final String LOG_REJECTED = "REJECTED";
    /** 已取消 */
    public static final String CANCELLED = "CANCELLED";

    // ==================== Prepayment 特有 ====================

    /** 已核销抵扣 */
    public static final String APPLIED = "APPLIED";

    // ==================== 状态检查辅助方法 ====================

    /** 是否为草稿状态（可修改/可删除） */
    public static boolean isDraft(String status) {
        return DRAFT.equals(status);
    }

    /** 是否为已确认状态（不可修改） */
    public static boolean isConfirmed(String status) {
        return CONFIRMED.equals(status);
    }

    /** 是否为已结清状态 */
    public static boolean isSettled(String status) {
        return SETTLED.equals(status);
    }

    /** 是否可冲销（已确认或已结清） */
    public static boolean isReversible(String status) {
        return CONFIRMED.equals(status) || SETTLED.equals(status);
    }

    /** 是否可修改（仅草稿） */
    public static boolean isModifiable(String status) {
        return DRAFT.equals(status);
    }

    /** 是否可取消（仅草稿或已提交） */
    public static boolean isCancellable(String status) {
        return DRAFT.equals(status) || SUBMITTED.equals(status);
    }

    /** 是否可提交（仅草稿） */
    public static boolean isSubmitable(String status) {
        return DRAFT.equals(status);
    }

    /** 是否可审批通过（仅已提交） */
    public static boolean isApprovable(String status) {
        return SUBMITTED.equals(status);
    }

    /** 是否可驳回（仅已提交） */
    public static boolean isRejectable(String status) {
        return SUBMITTED.equals(status);
    }

    /** 是否可反核销（仅已确认或已记账） */
    public static boolean isSettlementReversible(String status) {
        return CONFIRMED.equals(status) || VOUCHERED.equals(status);
    }

    /** 是否可生成凭证（仅已确认） */
    public static boolean isVoucherable(String status) {
        return CONFIRMED.equals(status);
    }

    /**
     * 校验状态转换是否合法.
     * @param from 当前状态
     * @param to 目标状态
     * @return 是否允许转换
     */
    public static boolean canTransition(String from, String to) {
        if (from == null || to == null) return false;
        return switch (from) {
            case DRAFT -> SUBMITTED.equals(to) || CANCELLED.equals(to);
            case SUBMITTED -> CONFIRMED.equals(to) || REJECTED.equals(to) || CANCELLED.equals(to);
            case CONFIRMED -> VOUCHERED.equals(to);
            case VOUCHERED -> REVERSED.equals(to);
            default -> false;
        };
    }
}
