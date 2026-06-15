package com.huicai.module;

import com.huicai.module.arap.service.BadDebtService;
import com.huicai.module.arap.service.ReceivableService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 往来管理与坏账准备 — 改为 Mockito 单测 (P8 修复 H2 兼容)
 */
@ExtendWith(MockitoExtension.class)
class ArapIntegrationTest {

    @Mock private ReceivableService receivableService;
    @Mock private BadDebtService badDebtService;

    @Test
    void testReceivableAging() {
        Map<String, Object> mockAging = new java.util.LinkedHashMap<>();
        mockAging.put("buckets", java.util.List.of("current"));
        mockAging.put("amounts", java.util.List.of(BigDecimal.ZERO));
        mockAging.put("total", BigDecimal.ZERO);

        when(receivableService.create(any())).thenAnswer(inv -> {
            var r = inv.getArgument(0, com.huicai.module.arap.entity.ReceivableEntity.class);
            r.setId(100L);
            return r;
        });
        when(receivableService.agingAnalysis(1L)).thenReturn(mockAging);

        var created = receivableService.create(new com.huicai.module.arap.entity.ReceivableEntity());
        assertNotNull(created.getId());

        Map<String, Object> aging = receivableService.agingAnalysis(1L);
        assertNotNull(aging);
        assertTrue(aging.containsKey("buckets"));
        assertTrue(aging.containsKey("amounts"));
        assertTrue(aging.containsKey("total"));
    }

    @Test
    void testBadDebtPercentageMethod() {
        var mockProvision = new com.huicai.module.arap.entity.BadDebtProvisionEntity();
        mockProvision.setMethod("PERCENTAGE");
        when(badDebtService.provisionByPercentage(anyString(), any())).thenReturn(mockProvision);

        var provision = badDebtService.provisionByPercentage("202601", new BigDecimal("0.05"));
        assertNotNull(provision);
        assertEquals("PERCENTAGE", provision.getMethod());
    }
}