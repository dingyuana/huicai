package com.huicai.sme.arap.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.sme.arap.entity.BadDebtProvisionEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BadDebtProvisionStateMachineService 单元测试.
 */
class BadDebtProvisionStateMachineServiceImplTest {

    private final BadDebtProvisionStateMachineServiceImpl service = new BadDebtProvisionStateMachineServiceImpl();

    private BadDebtProvisionEntity entityWithStatus(String status) {
        BadDebtProvisionEntity e = new BadDebtProvisionEntity();
        e.setId(1L);
        e.setStatus(status);
        return e;
    }

    @Test
    @DisplayName("assertConfirmable_DRAFT_通过")
    void assertConfirmable_draft_passes() {
        assertDoesNotThrow(() -> service.assertConfirmable(entityWithStatus("DRAFT")));
    }

    @Test
    @DisplayName("assertConfirmable_CONFIRMED_抛异常")
    void assertConfirmable_confirmed_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertConfirmable(entityWithStatus("CONFIRMED")));
        assertTrue(ex.getMessage().contains("不可确认"));
    }

    @Test
    @DisplayName("assertVoucherable_CONFIRMED_通过")
    void assertVoucherable_confirmed_passes() {
        assertDoesNotThrow(() -> service.assertVoucherable(entityWithStatus("CONFIRMED")));
    }

    @Test
    @DisplayName("assertVoucherable_DRAFT_抛异常")
    void assertVoucherable_draft_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertVoucherable(entityWithStatus("DRAFT")));
    }

    // ====== H-12 负向断言补充：多状态非法值覆盖，确保"不该做的没做" ======

    @Test
    @DisplayName("assertConfirmable_VOUCHERED_抛异常_已生成凭证不可确认")
    void assertConfirmable_vouchered_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertConfirmable(entityWithStatus("VOUCHERED")));
    }

    @Test
    @DisplayName("assertConfirmable_REVERSED_抛异常_已红冲不可确认")
    void assertConfirmable_reversed_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertConfirmable(entityWithStatus("REVERSED")));
    }

    @Test
    @DisplayName("assertVoucherable_REVERSED_抛异常_已红冲不可生成凭证")
    void assertVoucherable_reversed_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertVoucherable(entityWithStatus("REVERSED")));
    }

    @Test
    @DisplayName("isConfirmed_VOUCHERED_返回false")
    void isConfirmed_vouchered_false() {
        assertFalse(service.isConfirmed(entityWithStatus("VOUCHERED")));
    }

    @Test
    @DisplayName("isVoucherable_DRAFT_返回false")
    void isVoucherable_draft_false() {
        assertFalse(service.isVoucherable(entityWithStatus("DRAFT")));
    }

    @Test
    @DisplayName("isModifiable_VOUCHERED_返回false_已生成凭证不可修改")
    void isModifiable_vouchered_false() {
        assertFalse(service.isModifiable(entityWithStatus("VOUCHERED")));
    }
}