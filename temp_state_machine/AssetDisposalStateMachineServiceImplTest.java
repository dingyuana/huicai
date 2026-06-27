package com.huicai.module.asset.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.asset.entity.AssetDisposalEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AssetDisposalStateMachineService 单元测试.
 */
class AssetDisposalStateMachineServiceImplTest {

    private final AssetDisposalStateMachineServiceImpl service = new AssetDisposalStateMachineServiceImpl();

    private AssetDisposalEntity entityWithStatus(String status) {
        AssetDisposalEntity e = new AssetDisposalEntity();
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
        assertTrue(ex.getMessage().contains("不可审批"));
    }

    @Test
    @DisplayName("assertCompletable_APPROVED_通过")
    void assertCompletable_approved_passes() {
        assertDoesNotThrow(() -> service.assertCompletable(entityWithStatus("APPROVED")));
    }

    @Test
    @DisplayName("assertCompletable_COMPLETED_抛异常")
    void assertCompletable_completed_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertCompletable(entityWithStatus("COMPLETED")));
    }
}