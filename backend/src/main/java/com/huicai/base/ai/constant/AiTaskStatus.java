package com.huicai.base.ai.constant;

/**
 * AI 任务状态常量.
 * <p>
 * 使用 String 常量而非 Enum，保持与数据库 VARCHAR 兼容、与 MyBatis-Plus 直接字段赋值兼容。
 * </p>
 */
public final class AiTaskStatus {

    private AiTaskStatus() {}

    /** 待处理 */
    public static final String PENDING = "PENDING";

    /** 处理中 */
    public static final String PROCESSING = "PROCESSING";

    /** 已完成（待人工确认） */
    public static final String COMPLETED = "COMPLETED";

    /** 已失败 */
    public static final String FAILED = "FAILED";

    /** 已确认并应用 */
    public static final String APPLIED = "APPLIED";

    /** 已驳回 */
    public static final String REJECTED = "REJECTED";

    // ==================== 检查方法 ====================

    public static boolean isPending(String status) {
        return PENDING.equals(status);
    }

    public static boolean isProcessing(String status) {
        return PROCESSING.equals(status);
    }

    public static boolean isCompleted(String status) {
        return COMPLETED.equals(status);
    }

    public static boolean isFailed(String status) {
        return FAILED.equals(status);
    }

    public static boolean isApplied(String status) {
        return APPLIED.equals(status);
    }

    public static boolean isRejected(String status) {
        return REJECTED.equals(status);
    }

    /** 是否可启动处理 (PENDING → PROCESSING) */
    public static boolean isProcessable(String status) {
        return PENDING.equals(status);
    }

    /** 是否可完成 (PROCESSING → COMPLETED) */
    public static boolean isCompletable(String status) {
        return PROCESSING.equals(status);
    }

    /** 是否可应用 (COMPLETED → APPLIED，需人工确认) */
    public static boolean isApplicable(String status) {
        return COMPLETED.equals(status);
    }

    /** 是否可驳回 (COMPLETED → REJECTED) */
    public static boolean isRejectable(String status) {
        return COMPLETED.equals(status);
    }

    /** 是否为终态 (APPLIED/REJECTED/FAILED) */
    public static boolean isTerminal(String status) {
        return APPLIED.equals(status) || REJECTED.equals(status) || FAILED.equals(status);
    }
}