package com.huicai.sme.asset.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.sme.asset.constant.AssetStatus;
import com.huicai.sme.asset.entity.AssetCardEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AssetCardStateMachineService 单元测试.
 * <p>H-11 修复：补齐资产卡片状态机零测试覆盖。
 * <p>覆盖 4 个 assert* 前置校验方法 + 4 个 is* 查询方法，每个方法验证正向（合法状态通过）和负向（非法状态抛异常）。
 * <p>负向断言：每个 assert* 方法验证多个非法状态值，确保"不该做的没做"。
 */
class AssetCardStateMachineServiceImplTest {

    private final AssetCardStateMachineServiceImpl service = new AssetCardStateMachineServiceImpl();

    private AssetCardEntity entityWithStatus(String status) {
        AssetCardEntity e = new AssetCardEntity();
        e.setId(1L);
        e.setStatus(status);
        return e;
    }

    // ====== assertActivable (DRAFT → IN_USE) ======

    @Test
    @DisplayName("assertActivable_DRAFT_通过")
    void assertActivable_draft_passes() {
        assertDoesNotThrow(() -> service.assertActivable(entityWithStatus(AssetStatus.ASSET_CARD_DRAFT)));
    }

    @Test
    @DisplayName("assertActivable_IN_USE_抛异常_不可重复启用")
    void assertActivable_inUse_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertActivable(entityWithStatus(AssetStatus.ASSET_CARD_IN_USE)));
        assertTrue(ex.getMessage().contains("不可启用"));
    }

    @Test
    @DisplayName("assertActivable_DISPOSED_抛异常_已处置不可启用")
    void assertActivable_disposed_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertActivable(entityWithStatus(AssetStatus.ASSET_CARD_DISPOSED)));
    }

    @Test
    @DisplayName("assertActivable_SCRAPPED_抛异常_已报废不可启用")
    void assertActivable_scrapped_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertActivable(entityWithStatus(AssetStatus.ASSET_CARD_SCRAPPED)));
    }

    // ====== assertStoppable (IN_USE → IDLE) ======

    @Test
    @DisplayName("assertStoppable_IN_USE_通过")
    void assertStoppable_inUse_passes() {
        assertDoesNotThrow(() -> service.assertStoppable(entityWithStatus(AssetStatus.ASSET_CARD_IN_USE)));
    }

    @Test
    @DisplayName("assertStoppable_DRAFT_抛异常_草稿不可停用")
    void assertStoppable_draft_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertStoppable(entityWithStatus(AssetStatus.ASSET_CARD_DRAFT)));
        assertTrue(ex.getMessage().contains("不可停用"));
    }

    @Test
    @DisplayName("assertStoppable_DISPOSED_抛异常_已处置不可停用")
    void assertStoppable_disposed_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertStoppable(entityWithStatus(AssetStatus.ASSET_CARD_DISPOSED)));
    }

    // ====== assertRestartable (IDLE/STOPPED → IN_USE) ======

    @Test
    @DisplayName("assertRestartable_IDLE_通过")
    void assertRestartable_idle_passes() {
        assertDoesNotThrow(() -> service.assertRestartable(entityWithStatus(AssetStatus.ASSET_CARD_IDLE)));
    }

    @Test
    @DisplayName("assertRestartable_STOPPED_通过_兼容旧状态")
    void assertRestartable_stopped_passes() {
        assertDoesNotThrow(() -> service.assertRestartable(entityWithStatus(AssetStatus.ASSET_CARD_STOPPED)));
    }

    @Test
    @DisplayName("assertRestartable_IN_USE_抛异常_在用不可重复启用")
    void assertRestartable_inUse_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertRestartable(entityWithStatus(AssetStatus.ASSET_CARD_IN_USE)));
        assertTrue(ex.getMessage().contains("不可重新启用"));
    }

    @Test
    @DisplayName("assertRestartable_DISPOSED_抛异常_已处置不可重启")
    void assertRestartable_disposed_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertRestartable(entityWithStatus(AssetStatus.ASSET_CARD_DISPOSED)));
    }

    // ====== assertDisposable (IN_USE/IDLE/STOPPED → DISPOSED) ======

    @Test
    @DisplayName("assertDisposable_IN_USE_通过")
    void assertDisposable_inUse_passes() {
        assertDoesNotThrow(() -> service.assertDisposable(entityWithStatus(AssetStatus.ASSET_CARD_IN_USE)));
    }

    @Test
    @DisplayName("assertDisposable_IDLE_通过")
    void assertDisposable_idle_passes() {
        assertDoesNotThrow(() -> service.assertDisposable(entityWithStatus(AssetStatus.ASSET_CARD_IDLE)));
    }

    @Test
    @DisplayName("assertDisposable_STOPPED_通过_兼容旧状态")
    void assertDisposable_stopped_passes() {
        assertDoesNotThrow(() -> service.assertDisposable(entityWithStatus(AssetStatus.ASSET_CARD_STOPPED)));
    }

    @Test
    @DisplayName("assertDisposable_DRAFT_抛异常_草稿不可处置")
    void assertDisposable_draft_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertDisposable(entityWithStatus(AssetStatus.ASSET_CARD_DRAFT)));
        assertTrue(ex.getMessage().contains("不可处置"));
    }

    @Test
    @DisplayName("assertDisposable_DISPOSED_抛异常_已处置不可重复处置")
    void assertDisposable_disposed_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertDisposable(entityWithStatus(AssetStatus.ASSET_CARD_DISPOSED)));
    }

    // ====== isInUse (status == IN_USE) ======

    @Test
    @DisplayName("isInUse_IN_USE_返回true")
    void isInUse_inUse_true() {
        assertTrue(service.isInUse(entityWithStatus(AssetStatus.ASSET_CARD_IN_USE)));
    }

    @Test
    @DisplayName("isInUse_IDLE_返回false")
    void isInUse_idle_false() {
        assertFalse(service.isInUse(entityWithStatus(AssetStatus.ASSET_CARD_IDLE)));
    }

    @Test
    @DisplayName("isInUse_DRAFT_返回false")
    void isInUse_draft_false() {
        assertFalse(service.isInUse(entityWithStatus(AssetStatus.ASSET_CARD_DRAFT)));
    }

    // ====== isStopped (status == IDLE 或 STOPPED) ======

    @Test
    @DisplayName("isStopped_IDLE_返回true")
    void isStopped_idle_true() {
        assertTrue(service.isStopped(entityWithStatus(AssetStatus.ASSET_CARD_IDLE)));
    }

    @Test
    @DisplayName("isStopped_STOPPED_返回true_兼容旧状态")
    void isStopped_stopped_true() {
        assertTrue(service.isStopped(entityWithStatus(AssetStatus.ASSET_CARD_STOPPED)));
    }

    @Test
    @DisplayName("isStopped_IN_USE_返回false")
    void isStopped_inUse_false() {
        assertFalse(service.isStopped(entityWithStatus(AssetStatus.ASSET_CARD_IN_USE)));
    }

    @Test
    @DisplayName("isStopped_DISPOSED_返回false")
    void isStopped_disposed_false() {
        assertFalse(service.isStopped(entityWithStatus(AssetStatus.ASSET_CARD_DISPOSED)));
    }

    // ====== isDisposed (status == DISPOSED) ======

    @Test
    @DisplayName("isDisposed_DISPOSED_返回true")
    void isDisposed_disposed_true() {
        assertTrue(service.isDisposed(entityWithStatus(AssetStatus.ASSET_CARD_DISPOSED)));
    }

    @Test
    @DisplayName("isDisposed_IN_USE_返回false")
    void isDisposed_inUse_false() {
        assertFalse(service.isDisposed(entityWithStatus(AssetStatus.ASSET_CARD_IN_USE)));
    }

    @Test
    @DisplayName("isDisposed_SCRAPPED_返回false")
    void isDisposed_scrapped_false() {
        assertFalse(service.isDisposed(entityWithStatus(AssetStatus.ASSET_CARD_SCRAPPED)));
    }

    // ====== isModifiable (status == DRAFT) ======

    @Test
    @DisplayName("isModifiable_DRAFT_返回true")
    void isModifiable_draft_true() {
        assertTrue(service.isModifiable(entityWithStatus(AssetStatus.ASSET_CARD_DRAFT)));
    }

    @Test
    @DisplayName("isModifiable_IN_USE_返回false_在用不可修改")
    void isModifiable_inUse_false() {
        assertFalse(service.isModifiable(entityWithStatus(AssetStatus.ASSET_CARD_IN_USE)));
    }

    @Test
    @DisplayName("isModifiable_DISPOSED_返回false_已处置不可修改")
    void isModifiable_disposed_false() {
        assertFalse(service.isModifiable(entityWithStatus(AssetStatus.ASSET_CARD_DISPOSED)));
    }
}
