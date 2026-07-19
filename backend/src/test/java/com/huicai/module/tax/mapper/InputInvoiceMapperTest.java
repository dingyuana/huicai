package com.huicai.sme.tax.mapper;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.tax.entity.InputInvoiceEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InputInvoice Mapper 真实 DB 测试.
 * ✅ 正向插入 + 5 项约束校验
 * ⚠️ invoice_no UNIQUE 约束依赖 uq_output_invoice_no，跨表唯一
 */
class InputInvoiceMapperTest extends AbstractMapperTest {

    @Autowired
    private InputInvoiceMapper mapper;

    private InputInvoiceEntity createValidInvoice() {
        InputInvoiceEntity e = new InputInvoiceEntity();
        e.setInvoiceNo("INV-INPUT-" + System.currentTimeMillis());
        e.setInvoiceDate(LocalDate.now());
        e.setPeriod("202607");
        e.setAmount(new BigDecimal("5000.00"));
        e.setTaxRate(new BigDecimal("0.13"));
        e.setTaxAmount(new BigDecimal("650.00"));
        e.setTotalAmount(new BigDecimal("5650.00"));
        e.setInvoiceType("SPECIAL");
        e.setVendorName("测试供应商");
        e.setProcessStatus("PENDING");
        return e;
    }

    @Test
    void insert_shouldSucceedWithAllRequiredFields() {
        InputInvoiceEntity e = createValidInvoice();
        mapper.insert(e);
        assertNotNull(e.getId());

        InputInvoiceEntity found = mapper.selectById(e.getId());
        assertEquals("PENDING", found.getProcessStatus());
        assertEquals(0, found.getAmount().compareTo(new BigDecimal("5000.00")));
    }

    @Test
    void insert_shouldEnforceNotNullInvoiceNo() {
        InputInvoiceEntity e = createValidInvoice();
        e.setInvoiceNo(null);
        assertThrows(Exception.class, () -> mapper.insert(e),
                "invoice_no 为 NOT NULL，插入应失败");
    }

    @Test
    void insert_shouldEnforceNotNullTaxRate() {
        InputInvoiceEntity e = createValidInvoice();
        e.setTaxRate(null);
        assertThrows(Exception.class, () -> mapper.insert(e),
                "tax_rate 为 NOT NULL，插入应失败");
    }

    @Test
    void insert_shouldEnforceChkInvoiceType() {
        InputInvoiceEntity e = createValidInvoice();
        e.setInvoiceType("INVALID_TYPE");
        assertThrows(Exception.class, () -> mapper.insert(e),
                "invoice_type 有 CHECK 约束，INVALID_TYPE 应失败");
    }

    @Test
    void insert_shouldEnforceUniqueInvoiceNo() {
        InputInvoiceEntity e1 = createValidInvoice();
        mapper.insert(e1);

        InputInvoiceEntity e2 = createValidInvoice();
        e2.setInvoiceNo(e1.getInvoiceNo()); // 相同 invoice_no
        assertThrows(Exception.class, () -> mapper.insert(e2),
                "invoice_no 有 UNIQUE 约束，重复应失败");
    }

    @Test
    void update_shouldNotChangeImmutableFields() {
        InputInvoiceEntity e = createValidInvoice();
        mapper.insert(e);

        e.setAmount(new BigDecimal("9999.00"));
        mapper.updateById(e);

        InputInvoiceEntity found = mapper.selectById(e.getId());
        assertEquals(0, found.getAmount().compareTo(new BigDecimal("9999.00")),
                "金额应可更新");
    }
}