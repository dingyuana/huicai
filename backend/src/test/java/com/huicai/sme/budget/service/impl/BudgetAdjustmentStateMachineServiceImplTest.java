package com.huicai.sme.budget.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.sme.budget.entity.BudgetAdjustmentEntity;
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

    // ====== H-12 负向断言补充：多状态非法值覆盖，确保"不该做的没做" ======

    @Test
    @DisplayName("assertApprovable_EXECUTED_抛异常_已执行不可批准")
    void assertApprovable_executed_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertApprovable(entityWithStatus("EXECUTED")));
    }

    @Test
    @DisplayName("assertExecutable_PENDING_消息包含不可执行")
    void assertExecutable_pending_messageContains() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertExecutable(entityWithStatus("PENDING")));
        assertTrue(ex.getMessage().contains("不可执行"));
    }

    @Test
    @DisplayName("assertExecutable_EXECUTED_抛异常_已执行不可重复执行")
    void assertExecutable_executed_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertExecutable(entityWithStatus("EXECUTED")));
    }

    @Test
    @DisplayName("isApproved_PENDING_返回false")
    void isApproved_pending_false() {
        assertFalse(service.isApproved(entityWithStatus("PENDING")));
    }

    @Test
    @DisplayName("isExecuted_PENDING_返回false")
    void isExecuted_pending_false() {
        assertFalse(service.isExecuted(entityWithStatus("PENDING")));
    }

    @Test
    @DisplayName("isExecuted_APPROVED_返回false")
    void isExecuted_approved_false() {
        assertFalse(service.isExecuted(entityWithStatus("APPROVED")));
    }

    @Test
    @DisplayName("isModifiable_APPROVED_返回false_已批准不可修改")
    void isModifiable_approved_false() {
        assertFalse(service.isModifiable(entityWithStatus("APPROVED")));
    }

    @Test
    @DisplayName("isModifiable_EXECUTED_返回false_已执行不可修改")
    void isModifiable_executed_false() {
        assertFalse(service.isModifiable(entityWithStatus("EXECUTED")));
    }
}