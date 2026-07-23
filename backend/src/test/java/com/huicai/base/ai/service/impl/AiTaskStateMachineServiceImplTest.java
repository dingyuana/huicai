package com.huicai.base.ai.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.base.ai.constant.AiTaskStatus;
import com.huicai.base.ai.entity.AiTaskEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AiTaskStateMachineServiceImplTest {

    private final AiTaskStateMachineServiceImpl service = new AiTaskStateMachineServiceImpl();

    private AiTaskEntity stub(String status) {
        AiTaskEntity e = new AiTaskEntity();
        e.setId(1L);
        e.setStatus(status);
        return e;
    }

    @Test
    void assertProcessable_PENDING_通过() {
        AiTaskEntity e = stub(AiTaskStatus.PENDING);
        assertDoesNotThrow(() -> service.assertProcessable(e));
    }

    @Test
    void assertProcessable_非PENDING_抛异常() {
        AiTaskEntity e = stub(AiTaskStatus.PROCESSING);
        assertThrows(BusinessException.class, () -> service.assertProcessable(e));
    }

    @Test
    void assertCompletable_PROCESSING_通过() {
        AiTaskEntity e = stub(AiTaskStatus.PROCESSING);
        assertDoesNotThrow(() -> service.assertCompletable(e));
    }

    @Test
    void assertCompletable_非PROCESSING_抛异常() {
        AiTaskEntity e = stub(AiTaskStatus.COMPLETED);
        assertThrows(BusinessException.class, () -> service.assertCompletable(e));
    }

    @Test
    void assertFailable_PROCESSING_通过() {
        AiTaskEntity e = stub(AiTaskStatus.PROCESSING);
        assertDoesNotThrow(() -> service.assertFailable(e));
    }

    @Test
    void assertFailable_非PROCESSING_抛异常() {
        AiTaskEntity e = stub(AiTaskStatus.PENDING);
        assertThrows(BusinessException.class, () -> service.assertFailable(e));
    }

    @Test
    void assertApplicable_COMPLETED_通过() {
        AiTaskEntity e = stub(AiTaskStatus.COMPLETED);
        assertDoesNotThrow(() -> service.assertApplicable(e));
    }

    @Test
    void assertApplicable_非COMPLETED_抛异常() {
        AiTaskEntity e = stub(AiTaskStatus.PROCESSING);
        assertThrows(BusinessException.class, () -> service.assertApplicable(e));
    }

    @Test
    void assertRejectable_COMPLETED_通过() {
        AiTaskEntity e = stub(AiTaskStatus.COMPLETED);
        assertDoesNotThrow(() -> service.assertRejectable(e));
    }

    @Test
    void assertRejectable_非COMPLETED_抛异常() {
        AiTaskEntity e = stub(AiTaskStatus.PENDING);
        assertThrows(BusinessException.class, () -> service.assertRejectable(e));
    }

    @Test
    void isCompleted_COMPLETED_返回True() {
        AiTaskEntity e = stub(AiTaskStatus.COMPLETED);
        assertTrue(service.isCompleted(e));
    }

    @Test
    void isCompleted_非COMPLETED_返回False() {
        AiTaskEntity e = stub(AiTaskStatus.PROCESSING);
        assertFalse(service.isCompleted(e));
    }

    // ====== H-12 负向断言补充：错误消息断言 + 多状态非法值覆盖 ======

    @Test
    @DisplayName("assertProcessable_FAILED_抛异常_已失败不可启动")
    void assertProcessable_failed_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertProcessable(stub(AiTaskStatus.FAILED)));
    }

    @Test
    @DisplayName("assertProcessable_APPLIED_抛异常_已应用不可启动")
    void assertProcessable_applied_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertProcessable(stub(AiTaskStatus.APPLIED)));
    }

    @Test
    @DisplayName("assertCompletable_COMPLETED_抛异常_已完成不可重复完成")
    void assertCompletable_completed_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertCompletable(stub(AiTaskStatus.COMPLETED)));
    }

    @Test
    @DisplayName("assertFailable_COMPLETED_抛异常_已完成不可标记失败")
    void assertFailable_completed_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertFailable(stub(AiTaskStatus.COMPLETED)));
    }

    @Test
    @DisplayName("assertApplicable_REJECTED_抛异常_已驳回不可应用")
    void assertApplicable_rejected_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertApplicable(stub(AiTaskStatus.REJECTED)));
    }

    @Test
    @DisplayName("assertRejectable_APPLIED_抛异常_已应用不可驳回")
    void assertRejectable_applied_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertRejectable(stub(AiTaskStatus.APPLIED)));
    }

    @Test
    @DisplayName("assertProcessable_消息包含不可启动处理")
    void assertProcessable_messageContains() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertProcessable(stub(AiTaskStatus.PROCESSING)));
        assertTrue(ex.getMessage().contains("不可启动处理"));
    }

    @Test
    @DisplayName("assertCompletable_消息包含不可完成")
    void assertCompletable_messageContains() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertCompletable(stub(AiTaskStatus.PENDING)));
        assertTrue(ex.getMessage().contains("不可完成"));
    }

    @Test
    @DisplayName("assertFailable_消息包含不可标记失败")
    void assertFailable_messageContains() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertFailable(stub(AiTaskStatus.PENDING)));
        assertTrue(ex.getMessage().contains("不可标记失败"));
    }

    @Test
    @DisplayName("assertApplicable_消息包含不可应用")
    void assertApplicable_messageContains() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertApplicable(stub(AiTaskStatus.PROCESSING)));
        assertTrue(ex.getMessage().contains("不可应用"));
    }

    @Test
    @DisplayName("assertRejectable_消息包含不可驳回")
    void assertRejectable_messageContains() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertRejectable(stub(AiTaskStatus.PENDING)));
        assertTrue(ex.getMessage().contains("不可驳回"));
    }

    @Test
    @DisplayName("isApplied_APPLIED_返回True")
    void isApplied_applied_true() {
        assertTrue(service.isApplied(stub(AiTaskStatus.APPLIED)));
    }

    @Test
    @DisplayName("isApplied_COMPLETED_返回false")
    void isApplied_completed_false() {
        assertFalse(service.isApplied(stub(AiTaskStatus.COMPLETED)));
    }

    @Test
    @DisplayName("isTerminal_APPLIED_返回true")
    void isTerminal_applied_true() {
        assertTrue(service.isTerminal(stub(AiTaskStatus.APPLIED)));
    }

    @Test
    @DisplayName("isTerminal_REJECTED_返回true")
    void isTerminal_rejected_true() {
        assertTrue(service.isTerminal(stub(AiTaskStatus.REJECTED)));
    }

    @Test
    @DisplayName("isTerminal_FAILED_返回true")
    void isTerminal_failed_true() {
        assertTrue(service.isTerminal(stub(AiTaskStatus.FAILED)));
    }

    @Test
    @DisplayName("isTerminal_PENDING_返回false")
    void isTerminal_pending_false() {
        assertFalse(service.isTerminal(stub(AiTaskStatus.PENDING)));
    }

    @Test
    @DisplayName("isTerminal_PROCESSING_返回false")
    void isTerminal_processing_false() {
        assertFalse(service.isTerminal(stub(AiTaskStatus.PROCESSING)));
    }
}