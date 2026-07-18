package com.huicai.module.finance.constant;

import java.util.Set;

/**
 * 业务单据状态常量.
 * 状态机: DRAFT → SUBMITTED → APPROVED → VOUCHERED → PARTIALLY_RECONCILED/FULLY_RECONCILED
 * 分支: SUBMITTED → REJECTED, APPROVED/VOUCHERED → REVERSED, FULLY_RECONCILED → CLOSED
 */
public final class BusinessDocStatus {

    private BusinessDocStatus() {}

    // ====== 状态常量 ======
    /** 草稿 */
    public static final String DRAFT = "DRAFT";
    /** 已提交 */
    public static final String SUBMITTED = "SUBMITTED";
    /** 已审批 */
    public static final String APPROVED = "APPROVED";
    /** 已驳回 */
    public static final String REJECTED = "REJECTED";
    /** 已生成凭证 */
    public static final String VOUCHERED = "VOUCHERED";
    /** 部分核销 */
    public static final String PARTIALLY_RECONCILED = "PARTIALLY_RECONCILED";
    /** 完全核销 */
    public static final String FULLY_RECONCILED = "FULLY_RECONCILED";
    /** 已红冲 */
    public static final String REVERSED = "REVERSED";
    /** 已关闭 */
    public static final String CLOSED = "CLOSED";

    /** 所有终端状态（不可再流转） */
    private static final Set<String> TERMINAL_STATUSES = Set.of(
            REJECTED, FULLY_RECONCILED, REVERSED, CLOSED
    );

    /** 可生成凭证的状态 */
    private static final Set<String> VOUCHERABLE_STATUSES = Set.of(APPROVED);

    /** 可核销的状态 */
    private static final Set<String> RECONCILABLE_STATUSES = Set.of(VOUCHERED);

    // ====== 检查方法 ======

    /** 是否为草稿 */
    public static boolean isDraft(String status) {
        return DRAFT.equals(status);
    }

    /** 是否可提交（仅草稿可提交） */
    public static boolean isSubmittable(String status) {
        return DRAFT.equals(status);
    }

    /** 是否可审批（仅已提交可审批） */
    public static boolean isApprovable(String status) {
        return SUBMITTED.equals(status);
    }

    /** 是否可生成凭证 */
    public static boolean isVoucherable(String status) {
        return VOUCHERABLE_STATUSES.contains(status);
    }

    /** 是否可核销（仅已生成凭证的才可核销） */
    public static boolean isReconcilable(String status) {
        return RECONCILABLE_STATUSES.contains(status);
    }

    /**
     * 是否允许执行核销操作.
     * 核销操作将状态推进到 PARTIALLY_RECONCILED 或 FULLY_RECONCILED.
     */
    public static boolean canReconcile(String status) {
        return VOUCHERED.equals(status);
    }

    /** 是否为终端状态（不可再流转） */
    public static boolean isTerminal(String status) {
        return TERMINAL_STATUSES.contains(status);
    }

    /** 是否已核销（部分或完全） */
    public static boolean isReconciled(String status) {
        return PARTIALLY_RECONCILED.equals(status) || FULLY_RECONCILED.equals(status);
    }
}