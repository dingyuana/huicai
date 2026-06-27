package com.huicai.module.budget.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.budget.entity.BudgetEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BudgetStateMachineService 单元测试.
 */
class BudgetStateMachineServiceImplTest {

    private final BudgetStateMachineServiceImpl service = new BudgetStateMachineServiceImpl();

    private BudgetEntity entityWithStatus(String status) {
        BudgetEntity e = new BudgetEntity();
        e.setId(1L);
        e.setStatus(status);
        return e;
    }

    @Test
    @DisplayName("assertApprovable_DRAFT_通过")
    void assertApprovable_draft_passes() {
        assertDoesNotThrow(() -> service.assertApprovable(entityWithStatus("DRAFT")));
    }

    @Test
    @DisplayName("assertApprovable_APPROVED_抛异常")
    void assertApprovable_approved_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertApprovable(entityWithStatus("APPROVED")));
        assertTrue(ex.getMessage().contains("不可审批"));
    }

    @Test
    @DisplayName("assertActivable_APPROVED_通过")
    void assertActivable_approved_passes() {
        assertDoesNotThrow(() -> service.assertActivable(entityWithStatus("APPROVED")));
    }

    @Test
    @DisplayName("assertActivable_ACTIVE_抛异常")
    void assertActivable_active_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertActivable(entityWithStatus("ACTIVE")));
    }
}