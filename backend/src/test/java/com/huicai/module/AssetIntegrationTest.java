package com.huicai.module;

import com.huicai.module.asset.entity.AssetCardEntity;
import com.huicai.module.asset.service.AssetCardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 固定资产模块集成测试
 * 验证资产创建、折旧计算、状态管理
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:asset_test",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class AssetIntegrationTest {

    @Autowired
    private AssetCardService assetCardService;

    @Test
    void testStraightLineDepreciation() {
        AssetCardEntity card = new AssetCardEntity();
        card.setAssetCode("FA-001");
        card.setAssetName("测试办公电脑");
        card.setCategoryId(1L);
        card.setAcquisitionDate(java.time.LocalDate.of(2026, 1, 15));
        card.setOriginalValue(new BigDecimal("12000.00"));
        card.setResidualValue(new BigDecimal("600.00"));
        card.setUsefulLife(5);
        card.setDepreciationMethod("STRAIGHT_LINE");
        card.setStatus("IN_USE");
        AssetCardEntity created = assetCardService.create(card);
        assertNotNull(created.getId());
        assertEquals("IN_USE", created.getStatus());

        BigDecimal monthlyDep = assetCardService.calculateDepreciation(created, "202602");
        assertNotNull(monthlyDep);
        assertTrue(monthlyDep.compareTo(BigDecimal.ZERO) > 0);
        // (12000 - 600) / (5 * 12) = 190.00
        assertEquals(0, monthlyDep.compareTo(new BigDecimal("190.00")));
    }

    @Test
    void testDoubleDecliningDepreciation() {
        AssetCardEntity card = new AssetCardEntity();
        card.setAssetCode("FA-002");
        card.setAssetName("测试运输工具");
        card.setCategoryId(3L);
        card.setAcquisitionDate(java.time.LocalDate.of(2026, 1, 15));
        card.setOriginalValue(new BigDecimal("200000.00"));
        card.setResidualValue(new BigDecimal("10000.00"));
        card.setUsefulLife(8);
        card.setDepreciationMethod("DOUBLE_DECLINING");
        card.setStatus("IN_USE");
        AssetCardEntity created = assetCardService.create(card);
        BigDecimal monthlyDep = assetCardService.calculateDepreciation(created, "202602");
        assertNotNull(monthlyDep);
        assertTrue(monthlyDep.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testAssetCardValidation() {
        AssetCardEntity card = new AssetCardEntity();
        card.setAssetCode("FA-003");
        card.setOriginalValue(BigDecimal.ZERO);
        card.setUsefulLife(0);
        card.setDepreciationMethod("INVALID_METHOD");
        card.setStatus("IN_USE");
        // 应当能处理零值
        BigDecimal dep = assetCardService.calculateDepreciation(card, "202602");
        assertNotNull(dep);
    }
}
