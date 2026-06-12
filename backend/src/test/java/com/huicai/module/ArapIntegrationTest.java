package com.huicai.module;

import com.huicai.module.arap.service.BadDebtService;
import com.huicai.module.arap.service.ReceivableService;
import com.huicai.module.arap.entity.ReceivableEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 往来管理与坏账准备集成测试
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:arap_test",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class ArapIntegrationTest {

    @Autowired
    private ReceivableService receivableService;

    @Autowired
    private BadDebtService badDebtService;

    @Test
    void testReceivableAging() {
        ReceivableEntity r = new ReceivableEntity();
        r.setCustomerId(1L);
        r.setPeriod("202601");
        r.setTxDate(LocalDate.of(2026, 1, 15));
        r.setAmount(new BigDecimal("10000"));
        r.setSettledAmount(BigDecimal.ZERO);
        r.setDueDate(LocalDate.of(2026, 2, 15));
        r.setSummary("测试应收");
        ReceivableEntity created = receivableService.create(r);
        assertNotNull(created.getId());

        Map<String, Object> aging = receivableService.agingAnalysis(1L);
        assertNotNull(aging);
        assertTrue(aging.containsKey("buckets"));
        assertTrue(aging.containsKey("amounts"));
        assertTrue(aging.containsKey("total"));
    }

    @Test
    void testBadDebtPercentageMethod() {
        java.util.Map<String, BigDecimal> ratios = new java.util.HashMap<>();
        ratios.put("current", new BigDecimal("0.01"));
        var provision = badDebtService.provisionByPercentage("202601", new BigDecimal("0.05"));
        assertNotNull(provision);
        assertEquals("PERCENTAGE", provision.getMethod());
    }
}
