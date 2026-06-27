package com.huicai.module.asset.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.asset.entity.AssetInventoryEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AssetInventoryStateMachineService 单元测试.
 */
class AssetInventoryStateMachineServiceImplTest {

    private final AssetInventoryStateMachineServiceImpl service = new AssetInventoryStateMachineServiceImpl();

    private AssetInventoryEntity entityWithStatus(String status) {
        AssetInventoryEntity e = new AssetInventoryEntity();
        e.setId(1L);
        e.setStatus(status);
        return e;
    }

    @Test
    @DisplayName("assertStartable_DRAFT_通过")
    void assertStartable_draft_passes() {
        assertDoesNotThrow(() -> service.assertStartable(entityWithStatus("DRAFT")));
    }

    @Test
    @DisplayName("assertStartable_IN_PROGRESS_抛异常")
    void assertStartable_inProgress_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertStartable(entityWithStatus("IN_PROGRESS")));
        assertTrue(ex.getMessage().contains("不可开始"));
    }

    @Test
    @DisplayName("assertCompletable_IN_PROGRESS_通过")
    void assertCompletable_inProgress_passes() {
        assertDoesNotThrow(() -> service.assertCompletable(entityWithStatus("IN_PROGRESS")));
    }

    @Test
    @DisplayName("assertCompletable_COMPLETED_抛异常")
    void assertCompletable_completed_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertCompletable(entityWithStatus("COMPLETED")));
    }
}