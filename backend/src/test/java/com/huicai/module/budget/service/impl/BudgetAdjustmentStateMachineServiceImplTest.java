package com.huicai.module.budget.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.budget.entity.BudgetAdjustmentEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BudgetAdjustmentStateMachineService 单元测试.
 */
class BudgetAdjustmentStateMachineServiceImplTest {

    private final BudgetAdjustmentStateMachineServiceImpl service = new BudgetAdjustmentStateMachineServiceImpl();

    private BudgetAdjustmentEntity entityWithStatus(String status) {
        BudgetAdjustmentEntity e = new BudgetAdjustmentEntity();
        e.setId(1L);
        e.setStatus(status);
        return e;
    }

    @Test
    @DisplayName("assertApprovable_PENDING_通过")
    void assertApprovable_pending_passes() {
        assertDoesNotThrow(() -> service.assertApprovable(entityWithStatus("PENDING")));
    }

    @Test
    @DisplayName("assertApprovable_APPROVED_抛异常")
    void assertApprovable_approved_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertApprovable(entityWithStatus("APPROVED")));
        assertTrue(ex.getMessage().contains("不可批准"));
    }

    @Test
    @DisplayName("assertExecutable_APPROVED_通过")
    void assertExecutable_approved_passes() {
        assertDoesNotThrow(() -> service.assertExecutable(entityWithStatus("APPROVED")));
    }

    @Test
    @DisplayName("assertExecutable_PENDING_抛异常")
    void assertExecutable_pending_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertExecutable(entityWithStatus("PENDING")));
    }
}