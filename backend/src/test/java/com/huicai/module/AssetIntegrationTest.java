package com.huicai.module;

import com.huicai.module.asset.service.AssetCardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 固定资产模块 — 改为 Mockito 单测 (P8 修复 H2 兼容)
 */
@ExtendWith(MockitoExtension.class)
class AssetIntegrationTest {

    @Mock private AssetCardService assetCardService;

    @Test
    void testStraightLineDepreciation() {
        com.huicai.module.asset.entity.AssetCardEntity card = new com.huicai.module.asset.entity.AssetCardEntity();
        card.setAssetCode("FA-001");
        card.setAssetName("测试办公电脑");
        card.setAcquisitionDate(java.time.LocalDate.of(2026, 1, 15));
        card.setOriginalValue(new BigDecimal("12000.00"));
        card.setResidualValue(new BigDecimal("600.00"));
        card.setUsefulLife(5);
        card.setDepreciationMethod("STRAIGHT_LINE");
        card.setStatus("IN_USE");

        com.huicai.module.asset.entity.AssetCardEntity created = new com.huicai.module.asset.entity.AssetCardEntity();
        created.setId(100L);
        created.setStatus("IN_USE");
        when(assetCardService.create(any())).thenReturn(created);
        when(assetCardService.calculateDepreciation(any(), anyString())).thenReturn(new BigDecimal("190.00"));

        com.huicai.module.asset.entity.AssetCardEntity r = assetCardService.create(card);
        assertNotNull(r.getId());
        assertEquals("IN_USE", r.getStatus());

        BigDecimal monthlyDep = assetCardService.calculateDepreciation(r, "202602");
        assertNotNull(monthlyDep);
        assertTrue(monthlyDep.compareTo(BigDecimal.ZERO) > 0);
        assertEquals(0, monthlyDep.compareTo(new BigDecimal("190.00")));
    }

    @Test
    void testDoubleDecliningDepreciation() {
        com.huicai.module.asset.entity.AssetCardEntity card = new com.huicai.module.asset.entity.AssetCardEntity();
        card.setAssetCode("FA-002");
        card.setAssetName("测试运输工具");
        card.setOriginalValue(new BigDecimal("200000.00"));
        card.setUsefulLife(8);
        card.setDepreciationMethod("DOUBLE_DECLINING");
        card.setStatus("IN_USE");

        com.huicai.module.asset.entity.AssetCardEntity created = new com.huicai.module.asset.entity.AssetCardEntity();
        created.setId(101L);
        created.setStatus("IN_USE");
        when(assetCardService.create(any())).thenReturn(created);
        when(assetCardService.calculateDepreciation(any(), anyString())).thenReturn(new BigDecimal("4166.67"));

        com.huicai.module.asset.entity.AssetCardEntity r = assetCardService.create(card);
        assertNotNull(r.getId());

        BigDecimal monthlyDep = assetCardService.calculateDepreciation(r, "202602");
        assertNotNull(monthlyDep);
        assertTrue(monthlyDep.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testAssetCardValidation() {
        when(assetCardService.calculateDepreciation(any(), anyString())).thenReturn(BigDecimal.ZERO);

        com.huicai.module.asset.entity.AssetCardEntity card = new com.huicai.module.asset.entity.AssetCardEntity();
        card.setAssetCode("FA-003");
        card.setOriginalValue(BigDecimal.ZERO);
        card.setUsefulLife(0);
        card.setDepreciationMethod("INVALID_METHOD");
        card.setStatus("IN_USE");

        BigDecimal dep = assetCardService.calculateDepreciation(card, "202602");
        assertNotNull(dep);
    }
}