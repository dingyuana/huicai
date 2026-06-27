package com.huicai.module.asset.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.asset.entity.AssetCardEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AssetCardStateMachineService 单元测试.
 */
class AssetCardStateMachineServiceImplTest {

    private final AssetCardStateMachineServiceImpl service = new AssetCardStateMachineServiceImpl();

    private AssetCardEntity entityWithStatus(String status) {
        AssetCardEntity e = new AssetCardEntity();
        e.setId(1L);
        e.setStatus(status);
        return e;
    }

    @Test
    @DisplayName("assertActivable_PENDING_通过")
    void assertActivable_pending_passes() {
        assertDoesNotThrow(() -> service.assertActivable(entityWithStatus("PENDING")));
    }

    @Test
    @DisplayName("assertActivable_ACTIVE_抛异常")
    void assertActivable_active_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertActivable(entityWithStatus("ACTIVE")));
        assertTrue(ex.getMessage().contains("不可启用"));
    }

    @Test
    @DisplayName("assertDisposable_ACTIVE_通过")
    void assertDisposable_active_passes() {
        assertDoesNotThrow(() -> service.assertDisposable(entityWithStatus("ACTIVE")));
    }

    @Test
    @DisplayName("assertDisposable_DISPOSED_抛异常")
    void assertDisposable_disposed_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertDisposable(entityWithStatus("DISPOSED")));
    }
}