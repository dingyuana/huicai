package com.huicai.module.ai.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.ai.entity.AiTaskEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AiTaskStateMachineService 单元测试.
 * 覆盖 6 状态: PENDING → PROCESSING → COMPLETED → APPLIED/REJECTED
 *                  → FAILED
 */
class AiTaskStateMachineServiceImplTest {

    private final AiTaskStateMachineServiceImpl service = new AiTaskStateMachineServiceImpl();

    private AiTaskEntity entityWithStatus(String status) {
        AiTaskEntity e = new AiTaskEntity();
        e.setId(1L);
        e.setStatus(status);
        return e;
    }

    // ====== assertProcessable (PENDING → PROCESSING) ======

    @Test
    @DisplayName("assertProcessable_PENDING_通过")
    void assertProcessable_pending_passes() {
        assertDoesNotThrow(() -> service.assertProcessable(entityWithStatus("PENDING")));
    }

    @Test
    @DisplayName("assertProcessable_PROCESSING_抛异常")
    void assertProcessable_processing_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertProcessable(entityWithStatus("PROCESSING")));
        assertTrue(ex.getMessage().contains("不可启动处理"));
    }

    // ====== assertCompletable (PROCESSING → COMPLETED) ======

    @Test
    @DisplayName("assertCompletable_PROCESSING_通过")
    void assertCompletable_processing_passes() {
        assertDoesNotThrow(() -> service.assertCompletable(entityWithStatus("PROCESSING")));
    }

    @Test
    @DisplayName("assertCompletable_PENDING_抛异常")
    void assertCompletable_pending_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertCompletable(entityWithStatus("PENDING")));
    }

    // ====== assertFailable (PROCESSING → FAILED) ======

    @Test
    @DisplayName("assertFailable_PROCESSING_通过")
    void assertFailable_processing_passes() {
        assertDoesNotThrow(() -> service.assertFailable(entityWithStatus("PROCESSING")));
    }

    @Test
    @DisplayName("assertFailable_PENDING_抛异常")
    void assertFailable_pending_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertFailable(entityWithStatus("PENDING")));
    }

    // ====== assertApplicable (COMPLETED → APPLIED, 需人工确认) ======

    @Test
    @DisplayName("assertApplicable_COMPLETED_通过")
    void assertApplicable_completed_passes() {
        assertDoesNotThrow(() -> service.assertApplicable(entityWithStatus("COMPLETED")));
    }

    @Test
    @DisplayName("assertApplicable_PENDING_抛异常")
    void assertApplicable_pending_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertApplicable(entityWithStatus("PENDING")));
        assertTrue(ex.getMessage().contains("不可应用"));
    }

    // ====== assertRejectable (COMPLETED → REJECTED) ======

    @Test
    @DisplayName("assertRejectable_COMPLETED_通过")
    void assertRejectable_completed_passes() {
        assertDoesNotThrow(() -> service.assertRejectable(entityWithStatus("COMPLETED")));
    }

    @Test
    @DisplayName("assertRejectable_APPLIED_抛异常")
    void assertRejectable_applied_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertRejectable(entityWithStatus("APPLIED")));
    }

    // ====== 状态判断 ======

    @Test
    @DisplayName("isCompleted_COMPLETED_返回true")
    void isCompleted_true() {
        assertTrue(service.isCompleted(entityWithStatus("COMPLETED")));
    }

    @Test
    @DisplayName("isCompleted_PENDING_返回false")
    void isCompleted_false() {
        assertFalse(service.isCompleted(entityWithStatus("PENDING")));
    }

    @Test
    @DisplayName("isTerminal_APPLIED_返回true")
    void isTerminal_applied_true() {
        assertTrue(service.isTerminal(entityWithStatus("APPLIED")));
    }

    @Test
    @DisplayName("isTerminal_REJECTED_返回true")
    void isTerminal_rejected_true() {
        assertTrue(service.isTerminal(entityWithStatus("REJECTED")));
    }

    @Test
    @DisplayName("isTerminal_FAILED_返回true")
    void isTerminal_failed_true() {
        assertTrue(service.isTerminal(entityWithStatus("FAILED")));
    }

    @Test
    @DisplayName("isTerminal_PENDING_返回false")
    void isTerminal_pending_false() {
        assertFalse(service.isTerminal(entityWithStatus("PENDING")));
    }
}