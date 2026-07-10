package com.huicai.module.asset.constant;

/**
 * 固定资产模块状态常量.
 * <p>
 * 使用 String 常量而非 Enum，保持与数据库 VARCHAR 兼容、与 MyBatis-Plus 直接字段赋值兼容。
 * </p>
 */
public final class AssetStatus {

    private AssetStatus() {}

    // ==================== 资产卡片状态 ====================

    /** 新增待确认 */
    public static final String ASSET_CARD_DRAFT = "DRAFT";
    /** 在用 */
    public static final String ASSET_CARD_IN_USE = "IN_USE";
    /** 闲置（原 STOPPED，推荐使用） */
    public static final String ASSET_CARD_IDLE = "IDLE";
    /**
     * 停用.
     * @deprecated 请使用 {@link #ASSET_CARD_IDLE}
     */
    @Deprecated
    public static final String ASSET_CARD_STOPPED = "STOPPED";
    /** 已处置 */
    public static final String ASSET_CARD_DISPOSED = "DISPOSED";
    /** 已报废 */
    public static final String ASSET_CARD_SCRAPPED = "SCRAPPED";

    // ==================== 资产盘点状态 ====================

    /** 盘点中 */
    public static final String INVENTORY_IN_PROGRESS = "IN_PROGRESS";
    /** 已确认差异 */
    public static final String INVENTORY_CONFIRMED = "CONFIRMED";
    /** 已生成凭证 */
    public static final String INVENTORY_VOUCHERED = "VOUCHERED";

    // ==================== 资产处置状态 ====================

    /** 待审核 */
    public static final String DISPOSAL_PENDING = "PENDING";
    /** 已审核 */
    public static final String DISPOSAL_APPROVED = "APPROVED";
    /** 已完成 */
    public static final String DISPOSAL_COMPLETED = "COMPLETED";

    // ==================== 资产卡片检查方法 ====================

    public static boolean isAssetCardDraft(String status) {
        return ASSET_CARD_DRAFT.equals(status);
    }

    public static boolean isAssetCardInUse(String status) {
        return ASSET_CARD_IN_USE.equals(status);
    }

    public static boolean isAssetCardIdle(String status) {
        return ASSET_CARD_IDLE.equals(status);
    }

    public static boolean isAssetCardStopped(String status) {
        return ASSET_CARD_STOPPED.equals(status);
    }

    public static boolean isAssetCardDisposed(String status) {
        return ASSET_CARD_DISPOSED.equals(status);
    }

    public static boolean isAssetCardScrapped(String status) {
        return ASSET_CARD_SCRAPPED.equals(status);
    }

    public static boolean isAssetCardActive(String status) {
        return ASSET_CARD_IN_USE.equals(status) || ASSET_CARD_IDLE.equals(status) || ASSET_CARD_STOPPED.equals(status);
    }

    // ==================== 资产盘点检查方法 ====================

    public static boolean isInventoryInProgress(String status) {
        return INVENTORY_IN_PROGRESS.equals(status);
    }

    public static boolean isInventoryConfirmed(String status) {
        return INVENTORY_CONFIRMED.equals(status);
    }

    public static boolean isInventoryVouchered(String status) {
        return INVENTORY_VOUCHERED.equals(status);
    }

    // ==================== 资产处置检查方法 ====================

    public static boolean isDisposalPending(String status) {
        return DISPOSAL_PENDING.equals(status);
    }

    public static boolean isDisposalApproved(String status) {
        return DISPOSAL_APPROVED.equals(status);
    }

    public static boolean isDisposalCompleted(String status) {
        return DISPOSAL_COMPLETED.equals(status);
    }
}