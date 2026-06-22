package com.huicai.module.finance.constant;

/**
 * 凭证模块状态常量.
 * 核心 4 状态（status 字段）+ REJECTED/REVERSED 作为附属字段.
 * 详见 docs/需求分析书_发票与凭证状态机_V1.0.md §3.2
 * 2026-06-22 P22 创建
 */
public final class VoucherStatus {

    private VoucherStatus() {}

    // ====== status 字段 4 状态 ======
    public static final String DRAFT = "DRAFT";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String AUDITED = "AUDITED";
    public static final String POSTED = "POSTED";

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
    public static boolean isPosted(String status) {
        return POSTED.equals(status);
    }
    public static boolean isModifiable(String status) {
        // POSTED 不可修改（铁律）
        return !POSTED.equals(status);
    }
    public static boolean isReversible(String status) {
        // 仅 POSTED 可冲销
        return POSTED.equals(status);
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
