package com.huicai.module.ai.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.ai.constant.AiTaskStatus;
import com.huicai.module.ai.entity.AiTaskEntity;
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
}