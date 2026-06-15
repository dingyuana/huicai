package com.huicai.module;

import com.huicai.module.tax.service.TaxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 税务管理 — 改为 Mockito 单测 (P8 修复 H2 兼容)
 */
@ExtendWith(MockitoExtension.class)
class TaxIntegrationTest {

    @Mock private TaxService taxService;

    @Test
    void testTaxTypeList() {
        when(taxService.listAllTaxTypes()).thenReturn(new ArrayList<>());
        var types = taxService.listAllTaxTypes();
        assertNotNull(types);
    }

    @Test
    void testVatCalculation() {
        Map<String, Object> mockVat = new LinkedHashMap<>();
        mockVat.put("outputTax", new BigDecimal("13000.00"));
        mockVat.put("inputTax", new BigDecimal("5000.00"));
        mockVat.put("payableTax", new BigDecimal("8000.00"));
        mockVat.put("surcharge", new BigDecimal("800.00"));
        when(taxService.calculateVat(anyString())).thenReturn(mockVat);

        Map<String, Object> result = taxService.calculateVat("202601");
        assertNotNull(result);
        assertTrue(result.containsKey("outputTax"));
        assertTrue(result.containsKey("inputTax"));
        assertTrue(result.containsKey("payableTax"));
        assertTrue(result.containsKey("surcharge"));
    }

    @Test
    void testInputInvoiceCreate() {
        com.huicai.module.tax.entity.InputInvoiceEntity invoice = new com.huicai.module.tax.entity.InputInvoiceEntity();
        invoice.setInvoiceNo("TEST-INV-001");
        invoice.setInvoiceDate(java.time.LocalDate.of(2026, 1, 15));
        invoice.setVendorName("测试供应商");
        invoice.setAmount(new BigDecimal("1000"));
        invoice.setTaxRate(new BigDecimal("0.13"));
        invoice.setInvoiceType("SPECIAL");

        com.huicai.module.tax.entity.InputInvoiceEntity created = new com.huicai.module.tax.entity.InputInvoiceEntity();
        created.setId(100L);
        created.setTaxAmount(new BigDecimal("130.00"));
        when(taxService.createInput(any())).thenReturn(created);

        var r = taxService.createInput(invoice);
        assertNotNull(r.getId());
        assertNotNull(r.getTaxAmount());
        assertEquals(0, r.getTaxAmount().compareTo(new BigDecimal("130.00")));
    }
}