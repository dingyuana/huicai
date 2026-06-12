package com.huicai.module;

import com.huicai.module.tax.service.TaxService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 税务管理集成测试
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:tax_test",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class TaxIntegrationTest {

    @Autowired
    private TaxService taxService;

    @Test
    void testTaxTypeList() {
        var types = taxService.listAllTaxTypes();
        assertNotNull(types);
    }

    @Test
    void testVatCalculation() {
        Map<String, Object> result = taxService.calculateVat("202601");
        assertNotNull(result);
        assertTrue(result.containsKey("outputTax"));
        assertTrue(result.containsKey("inputTax"));
        assertTrue(result.containsKey("payableTax"));
        assertTrue(result.containsKey("surcharge"));
    }

    @Test
    void testInputInvoiceCreate() {
        var invoice = new com.huicai.module.tax.entity.InputInvoiceEntity();
        invoice.setInvoiceNo("TEST-INV-001");
        invoice.setInvoiceDate(java.time.LocalDate.of(2026, 1, 15));
        invoice.setVendorName("测试供应商");
        invoice.setAmount(new BigDecimal("1000"));
        invoice.setTaxRate(new BigDecimal("0.13"));
        invoice.setInvoiceType("SPECIAL");
        var created = taxService.createInput(invoice);
        assertNotNull(created.getId());
        assertNotNull(created.getTaxAmount());
        assertEquals(0, created.getTaxAmount().compareTo(new BigDecimal("130.00")));
    }
}
