package com.huicai.base.voucher.constant;

/**
 * 凭证模块状态常量.
 * 核心 5 状态（status 字段）+ REJECTED/REVERSED 作为附属字段.
 * 详见 docs/specs/P22-voucher-state-machine.md
 * 2026-06-22 P22 创建 | 2026-07-09 新增 CLOSED 状态
 */
public final class VoucherStatus {

    private VoucherStatus() {}

    // ====== status 字段 5 状态 ======
    public static final String DRAFT = "DRAFT";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String AUDITED = "AUDITED";
    public static final String POSTED = "POSTED";
    public static final String CLOSED = "CLOSED";

    // ====== 检查方法 ======
    public static boolean isDraft(String status) {
        return DRAFT.equals(status);
    }
    public static boolean isSubmittable(String status) {
        return DRAFT.equals(status);
    }
    public static boolean isAuditable(String status) {
        return SUBMITTED.equals(status);
    }
    public static boolean isPostable(String status) {
        return AUDITED.equals(status);
    }
    /** 仅 POSTED 可结账 */
    public static boolean isClosable(String status) {
        return POSTED.equals(status);
    }
    public static boolean isPosted(String status) {
        return POSTED.equals(status) || CLOSED.equals(status);
    }
    public static boolean isModifiable(String status) {
        // POSTED 和 CLOSED 不可修改（铁律）
        return !POSTED.equals(status) && !CLOSED.equals(status);
    }
    public static boolean isReversible(String status) {
        // POSTED 和 CLOSED 均可冲销
        return POSTED.equals(status) || CLOSED.equals(status);
    }
    public static boolean isRejected(String rejectedReason) {
        // REJECTED 状态 = status=DRAFT + rejectedReason 非空
        return rejectedReason != null && !rejectedReason.isBlank();
    }
    public static boolean isReversed(Long reversedFrom) {
        // REVERSED 状态 = status=POSTED + reversedFrom 非空
        return reversedFrom != null;
    }
}
