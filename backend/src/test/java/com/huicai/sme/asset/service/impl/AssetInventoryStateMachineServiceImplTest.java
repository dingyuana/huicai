package com.huicai.sme.asset.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.sme.asset.constant.AssetStatus;
import com.huicai.sme.asset.entity.AssetInventoryEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AssetInventoryStateMachineService 单元测试.
 * <p>H-11 修复：补齐资产盘点状态机零测试覆盖。
 * <p>覆盖 2 个 assert* 前置校验方法 + 3 个 is* 查询方法。
 * <p>负向断言：每个 assert* 方法验证多个非法状态值，确保"不该做的没做"。
 */
class AssetInventoryStateMachineServiceImplTest {

    private final AssetInventoryStateMachineServiceImpl service = new AssetInventoryStateMachineServiceImpl();

    private AssetInventoryEntity entityWithStatus(String status) {
        AssetInventoryEntity e = new AssetInventoryEntity();
        e.setId(1L);
        e.setStatus(status);
        return e;
    }

    // ====== assertConfirmable (IN_PROGRESS → CONFIRMED) ======

    @Test
    @DisplayName("assertConfirmable_IN_PROGRESS_通过")
    void assertConfirmable_inProgress_passes() {
        assertDoesNotThrow(() -> service.assertConfirmable(entityWithStatus(AssetStatus.INVENTORY_IN_PROGRESS)));
    }

    @Test
    @DisplayName("assertConfirmable_CONFIRMED_抛异常_不可重复确认")
    void assertConfirmable_confirmed_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertConfirmable(entityWithStatus(AssetStatus.INVENTORY_CONFIRMED)));
        assertTrue(ex.getMessage().contains("不可确认"));
    }

    @Test
    @DisplayName("assertConfirmable_VOUCHERED_抛异常_已生成凭证不可确认")
    void assertConfirmable_vouchered_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertConfirmable(entityWithStatus(AssetStatus.INVENTORY_VOUCHERED)));
    }

    // ====== assertVoucherable (CONFIRMED → VOUCHERED) ======

    @Test
    @DisplayName("assertVoucherable_CONFIRMED_通过")
    void assertVoucherable_confirmed_passes() {
        assertDoesNotThrow(() -> service.assertVoucherable(entityWithStatus(AssetStatus.INVENTORY_CONFIRMED)));
    }

    @Test
    @DisplayName("assertVoucherable_IN_PROGRESS_抛异常_盘点中不可生成凭证")
    void assertVoucherable_inProgress_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertVoucherable(entityWithStatus(AssetStatus.INVENTORY_IN_PROGRESS)));
        assertTrue(ex.getMessage().contains("不可生成凭证"));
    }

    @Test
    @DisplayName("assertVoucherable_VOUCHERED_抛异常_已生成凭证不可重复")
    void assertVoucherable_vouchered_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertVoucherable(entityWithStatus(AssetStatus.INVENTORY_VOUCHERED)));
    }

    // ====== isConfirmed (status == CONFIRMED) ======

    @Test
    @DisplayName("isConfirmed_CONFIRMED_返回true")
    void isConfirmed_confirmed_true() {
        assertTrue(service.isConfirmed(entityWithStatus(AssetStatus.INVENTORY_CONFIRMED)));
    }

    @Test
    @DisplayName("isConfirmed_IN_PROGRESS_返回false")
    void isConfirmed_inProgress_false() {
        assertFalse(service.isConfirmed(entityWithStatus(AssetStatus.INVENTORY_IN_PROGRESS)));
    }

    @Test
    @DisplayName("isConfirmed_VOUCHERED_返回false")
    void isConfirmed_vouchered_false() {
        assertFalse(service.isConfirmed(entityWithStatus(AssetStatus.INVENTORY_VOUCHERED)));
    }

    // ====== isVoucherable (status == VOUCHERED) ======

    @Test
    @DisplayName("isVoucherable_VOUCHERED_返回true")
    void isVoucherable_vouchered_true() {
        assertTrue(service.isVoucherable(entityWithStatus(AssetStatus.INVENTORY_VOUCHERED)));
    }

    @Test
    @DisplayName("isVoucherable_IN_PROGRESS_返回false")
    void isVoucherable_inProgress_false() {
        assertFalse(service.isVoucherable(entityWithStatus(AssetStatus.INVENTORY_IN_PROGRESS)));
    }

    @Test
    @DisplayName("isVoucherable_CONFIRMED_返回false")
    void isVoucherable_confirmed_false() {
        assertFalse(service.isVoucherable(entityWithStatus(AssetStatus.INVENTORY_CONFIRMED)));
    }

    // ====== isModifiable (status == IN_PROGRESS) ======

    @Test
    @DisplayName("isModifiable_IN_PROGRESS_返回true")
    void isModifiable_inProgress_true() {
        assertTrue(service.isModifiable(entityWithStatus(AssetStatus.INVENTORY_IN_PROGRESS)));
    }

    @Test
    @DisplayName("isModifiable_CONFIRMED_返回false_已确认不可修改")
    void isModifiable_confirmed_false() {
        assertFalse(service.isModifiable(entityWithStatus(AssetStatus.INVENTORY_CONFIRMED)));
    }

    @Test
    @DisplayName("isModifiable_VOUCHERED_返回false_已生成凭证不可修改")
    void isModifiable_vouchered_false() {
        assertFalse(service.isModifiable(entityWithStatus(AssetStatus.INVENTORY_VOUCHERED)));
    }
}
