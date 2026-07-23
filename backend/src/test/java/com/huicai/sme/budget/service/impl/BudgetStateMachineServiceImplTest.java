package com.huicai.sme.budget.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.sme.budget.constant.BudgetStatus;
import com.huicai.sme.budget.entity.BudgetEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BudgetStateMachineService 单元测试.
 * <p>H-11 修复：补齐预算状态机零测试覆盖。
 * <p>覆盖 3 个 assert* 前置校验方法 + 3 个 is* 查询方法。
 * <p>负向断言：每个 assert* 方法验证多个非法状态值，确保"不该做的没做"。
 */
class BudgetStateMachineServiceImplTest {

    private final BudgetStateMachineServiceImpl service = new BudgetStateMachineServiceImpl();

    private BudgetEntity entityWithStatus(String status) {
        BudgetEntity e = new BudgetEntity();
        e.setId(1L);
        e.setStatus(status);
        return e;
    }

    // ====== assertSubmittable (DRAFT → SUBMITTED) ======

    @Test
    @DisplayName("assertSubmittable_DRAFT_通过")
    void assertSubmittable_draft_passes() {
        assertDoesNotThrow(() -> service.assertSubmittable(entityWithStatus(BudgetStatus.BUDGET_DRAFT)));
    }

    @Test
    @DisplayName("assertSubmittable_SUBMITTED_抛异常_不可重复提交")
    void assertSubmittable_submitted_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertSubmittable(entityWithStatus(BudgetStatus.BUDGET_SUBMITTED)));
        assertTrue(ex.getMessage().contains("不可提交"));
    }

    @Test
    @DisplayName("assertSubmittable_APPROVED_抛异常_已批准不可提交")
    void assertSubmittable_approved_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertSubmittable(entityWithStatus(BudgetStatus.BUDGET_APPROVED)));
    }

    @Test
    @DisplayName("assertSubmittable_FROZEN_抛异常_已冻结不可提交")
    void assertSubmittable_frozen_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertSubmittable(entityWithStatus(BudgetStatus.BUDGET_FROZEN)));
    }

    // ====== assertApprovable (SUBMITTED → APPROVED) ======

    @Test
    @DisplayName("assertApprovable_SUBMITTED_通过")
    void assertApprovable_submitted_passes() {
        assertDoesNotThrow(() -> service.assertApprovable(entityWithStatus(BudgetStatus.BUDGET_SUBMITTED)));
    }

    @Test
    @DisplayName("assertApprovable_DRAFT_抛异常_草稿不可批准")
    void assertApprovable_draft_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertApprovable(entityWithStatus(BudgetStatus.BUDGET_DRAFT)));
        assertTrue(ex.getMessage().contains("不可批准"));
    }

    @Test
    @DisplayName("assertApprovable_APPROVED_抛异常_不可重复批准")
    void assertApprovable_approved_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertApprovable(entityWithStatus(BudgetStatus.BUDGET_APPROVED)));
    }

    @Test
    @DisplayName("assertApprovable_FROZEN_抛异常_已冻结不可批准")
    void assertApprovable_frozen_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertApprovable(entityWithStatus(BudgetStatus.BUDGET_FROZEN)));
    }

    // ====== assertFreezable (APPROVED → FROZEN) ======

    @Test
    @DisplayName("assertFreezable_APPROVED_通过")
    void assertFreezable_approved_passes() {
        assertDoesNotThrow(() -> service.assertFreezable(entityWithStatus(BudgetStatus.BUDGET_APPROVED)));
    }

    @Test
    @DisplayName("assertFreezable_DRAFT_抛异常_草稿不可冻结")
    void assertFreezable_draft_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertFreezable(entityWithStatus(BudgetStatus.BUDGET_DRAFT)));
        assertTrue(ex.getMessage().contains("不可冻结"));
    }

    @Test
    @DisplayName("assertFreezable_SUBMITTED_抛异常_已提交不可冻结")
    void assertFreezable_submitted_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertFreezable(entityWithStatus(BudgetStatus.BUDGET_SUBMITTED)));
    }

    @Test
    @DisplayName("assertFreezable_FROZEN_抛异常_不可重复冻结")
    void assertFreezable_frozen_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertFreezable(entityWithStatus(BudgetStatus.BUDGET_FROZEN)));
    }

    // ====== isApproved (status == APPROVED) ======

    @Test
    @DisplayName("isApproved_APPROVED_返回true")
    void isApproved_approved_true() {
        assertTrue(service.isApproved(entityWithStatus(BudgetStatus.BUDGET_APPROVED)));
    }

    @Test
    @DisplayName("isApproved_SUBMITTED_返回false")
    void isApproved_submitted_false() {
        assertFalse(service.isApproved(entityWithStatus(BudgetStatus.BUDGET_SUBMITTED)));
    }

    @Test
    @DisplayName("isApproved_FROZEN_返回false")
    void isApproved_frozen_false() {
        assertFalse(service.isApproved(entityWithStatus(BudgetStatus.BUDGET_FROZEN)));
    }

    // ====== isFrozen (status == FROZEN) ======

    @Test
    @DisplayName("isFrozen_FROZEN_返回true")
    void isFrozen_frozen_true() {
        assertTrue(service.isFrozen(entityWithStatus(BudgetStatus.BUDGET_FROZEN)));
    }

    @Test
    @DisplayName("isFrozen_APPROVED_返回false")
    void isFrozen_approved_false() {
        assertFalse(service.isFrozen(entityWithStatus(BudgetStatus.BUDGET_APPROVED)));
    }

    @Test
    @DisplayName("isFrozen_DRAFT_返回false")
    void isFrozen_draft_false() {
        assertFalse(service.isFrozen(entityWithStatus(BudgetStatus.BUDGET_DRAFT)));
    }

    // ====== isModifiable (status == DRAFT) ======

    @Test
    @DisplayName("isModifiable_DRAFT_返回true")
    void isModifiable_draft_true() {
        assertTrue(service.isModifiable(entityWithStatus(BudgetStatus.BUDGET_DRAFT)));
    }

    @Test
    @DisplayName("isModifiable_SUBMITTED_返回false_已提交不可修改")
    void isModifiable_submitted_false() {
        assertFalse(service.isModifiable(entityWithStatus(BudgetStatus.BUDGET_SUBMITTED)));
    }

    @Test
    @DisplayName("isModifiable_APPROVED_返回false_已批准不可修改")
    void isModifiable_approved_false() {
        assertFalse(service.isModifiable(entityWithStatus(BudgetStatus.BUDGET_APPROVED)));
    }
}
