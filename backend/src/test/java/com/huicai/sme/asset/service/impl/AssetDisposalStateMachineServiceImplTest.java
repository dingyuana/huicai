package com.huicai.sme.asset.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.sme.asset.constant.AssetStatus;
import com.huicai.sme.asset.entity.AssetDisposalEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AssetDisposalStateMachineService 单元测试.
 * <p>H-11 修复：补齐资产处置状态机零测试覆盖。
 * <p>覆盖 2 个 assert* 前置校验方法 + 3 个 is* 查询方法。
 * <p>负向断言：每个 assert* 方法验证多个非法状态值，确保"不该做的没做"。
 */
class AssetDisposalStateMachineServiceImplTest {

    private final AssetDisposalStateMachineServiceImpl service = new AssetDisposalStateMachineServiceImpl();

    private AssetDisposalEntity entityWithStatus(String status) {
        AssetDisposalEntity e = new AssetDisposalEntity();
        e.setId(1L);
        e.setStatus(status);
        return e;
    }

    // ====== assertApprovable (PENDING → APPROVED) ======

    @Test
    @DisplayName("assertApprovable_PENDING_通过")
    void assertApprovable_pending_passes() {
        assertDoesNotThrow(() -> service.assertApprovable(entityWithStatus(AssetStatus.DISPOSAL_PENDING)));
    }

    @Test
    @DisplayName("assertApprovable_APPROVED_抛异常_不可重复审批")
    void assertApprovable_approved_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertApprovable(entityWithStatus(AssetStatus.DISPOSAL_APPROVED)));
        assertTrue(ex.getMessage().contains("不可审核"));
    }

    @Test
    @DisplayName("assertApprovable_COMPLETED_抛异常_已完成不可审批")
    void assertApprovable_completed_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertApprovable(entityWithStatus(AssetStatus.DISPOSAL_COMPLETED)));
    }

    // ====== assertCompletable (APPROVED → COMPLETED) ======

    @Test
    @DisplayName("assertCompletable_APPROVED_通过")
    void assertCompletable_approved_passes() {
        assertDoesNotThrow(() -> service.assertCompletable(entityWithStatus(AssetStatus.DISPOSAL_APPROVED)));
    }

    @Test
    @DisplayName("assertCompletable_PENDING_抛异常_待审核不可完成")
    void assertCompletable_pending_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertCompletable(entityWithStatus(AssetStatus.DISPOSAL_PENDING)));
        assertTrue(ex.getMessage().contains("不可完成"));
    }

    @Test
    @DisplayName("assertCompletable_COMPLETED_抛异常_已完成不可重复完成")
    void assertCompletable_completed_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertCompletable(entityWithStatus(AssetStatus.DISPOSAL_COMPLETED)));
    }

    // ====== isApproved (status == APPROVED) ======

    @Test
    @DisplayName("isApproved_APPROVED_返回true")
    void isApproved_approved_true() {
        assertTrue(service.isApproved(entityWithStatus(AssetStatus.DISPOSAL_APPROVED)));
    }

    @Test
    @DisplayName("isApproved_PENDING_返回false")
    void isApproved_pending_false() {
        assertFalse(service.isApproved(entityWithStatus(AssetStatus.DISPOSAL_PENDING)));
    }

    @Test
    @DisplayName("isApproved_COMPLETED_返回false")
    void isApproved_completed_false() {
        assertFalse(service.isApproved(entityWithStatus(AssetStatus.DISPOSAL_COMPLETED)));
    }

    // ====== isCompleted (status == COMPLETED) ======

    @Test
    @DisplayName("isCompleted_COMPLETED_返回true")
    void isCompleted_completed_true() {
        assertTrue(service.isCompleted(entityWithStatus(AssetStatus.DISPOSAL_COMPLETED)));
    }

    @Test
    @DisplayName("isCompleted_PENDING_返回false")
    void isCompleted_pending_false() {
        assertFalse(service.isCompleted(entityWithStatus(AssetStatus.DISPOSAL_PENDING)));
    }

    @Test
    @DisplayName("isCompleted_APPROVED_返回false")
    void isCompleted_approved_false() {
        assertFalse(service.isCompleted(entityWithStatus(AssetStatus.DISPOSAL_APPROVED)));
    }

    // ====== isModifiable (status == PENDING) ======

    @Test
    @DisplayName("isModifiable_PENDING_返回true")
    void isModifiable_pending_true() {
        assertTrue(service.isModifiable(entityWithStatus(AssetStatus.DISPOSAL_PENDING)));
    }

    @Test
    @DisplayName("isModifiable_APPROVED_返回false_已审核不可修改")
    void isModifiable_approved_false() {
        assertFalse(service.isModifiable(entityWithStatus(AssetStatus.DISPOSAL_APPROVED)));
    }

    @Test
    @DisplayName("isModifiable_COMPLETED_返回false_已完成不可修改")
    void isModifiable_completed_false() {
        assertFalse(service.isModifiable(entityWithStatus(AssetStatus.DISPOSAL_COMPLETED)));
    }
}
