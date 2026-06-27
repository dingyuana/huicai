package com.huicai.module.arap.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.entity.BadDebtProvisionEntity;
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
}