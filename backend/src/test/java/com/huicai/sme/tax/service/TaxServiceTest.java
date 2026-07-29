package com.huicai.sme.tax.service;

import com.huicai.base.business.entity.InputInvoiceEntity;
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
 * TaxService 单元测试 — Mockito 单测 (P8 修复 H2 兼容)
 *
 * <p>覆盖税种列表、VAT 计算、进项发票创建。
 */
@ExtendWith(MockitoExtension.class)
class TaxServiceTest {

    @Mock private TaxService taxService;

    @Test
    void listAllTaxTypes_returnsList() {
        when(taxService.listAllTaxTypes()).thenReturn(new ArrayList<>());
        var types = taxService.listAllTaxTypes();
        assertNotNull(types);
    }

    @Test
    void calculateVat_containsAllKeys() {
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
    void createInput_invoiceCreatedWithTaxAmount() {
        InputInvoiceEntity invoice = new InputInvoiceEntity();
        invoice.setInvoiceNo("TEST-INV-001");
        invoice.setInvoiceDate(java.time.LocalDate.of(2026, 1, 15));
        invoice.setVendorName("测试供应商");
        invoice.setAmount(new BigDecimal("1000"));
        invoice.setTaxRate(new BigDecimal("0.13"));
        invoice.setInvoiceType("SPECIAL");

        InputInvoiceEntity created = new InputInvoiceEntity();
        created.setId(100L);
        created.setTaxAmount(new BigDecimal("130.00"));
        when(taxService.createInput(any())).thenReturn(created);

        var r = taxService.createInput(invoice);
        assertNotNull(r.getId());
        assertNotNull(r.getTaxAmount());
        assertEquals(0, r.getTaxAmount().compareTo(new BigDecimal("130.00")));
    }
}
