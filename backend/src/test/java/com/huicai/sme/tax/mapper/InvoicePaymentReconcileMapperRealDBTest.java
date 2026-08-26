package com.huicai.sme.tax.mapper;

import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.business.entity.InputInvoiceEntity;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.tax.dto.vo.InvoiceReconcileVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P58: 发票-收付款勾稽 Mapper 真实 DB 测试（SQL CASE 逻辑验证）.
 * 慢测试：需 Docker，本地跳过，CI 全量执行。
 */
class InvoicePaymentReconcileMapperRealDBTest extends AbstractMapperTest {

    @Autowired
    private InvoicePaymentReconcileMapper reconcileMapper;
    @Autowired
    private com.huicai.base.business.mapper.InputInvoiceMapper inputInvoiceMapper;
    @Autowired
    private com.huicai.base.business.mapper.BusinessDocMapper businessDocMapper;

    private InputInvoiceEntity invoice(Long id, BigDecimal total, String cert, String declared) {
        InputInvoiceEntity e = new InputInvoiceEntity();
        e.setId(id);
        e.setInvoiceNo("INV-RDB-" + id);
        e.setInvoiceDate(LocalDate.of(2026, 8, 1));
        e.setPeriod("202608");
        e.setVendorId(1L);
        e.setVendorName("供应商A");
        e.setAmount(total);
        e.setTaxRate(new BigDecimal("13"));
        e.setTaxAmount(total.multiply(new BigDecimal("0.13")));
        e.setTotalAmount(total);
        e.setInvoiceType("SPECIAL");
        e.setCertificationStatus(cert);
        e.setDeclaredStatus(declared);
        e.setStatus("VOUCHERED");
        e.setCreatedBy(1L);
        return e;
    }

    private BusinessDocEntity doc(Long invoiceId, BigDecimal settled) {
        BusinessDocEntity d = new BusinessDocEntity();
        d.setDocNo("DOC-RDB-" + invoiceId);
        d.setDocType("INVOICE_IN");
        d.setDocDate(LocalDate.of(2026, 8, 1));
        d.setPeriod("202608");
        d.setAmount(settled);
        d.setStatus("VOUCHERED");
        d.setSupplierId(1L);
        d.setSummary("供应商A");
        d.setInvoiceNo("INV-RDB-" + invoiceId);
        d.setInvoiceId(invoiceId);
        d.setSettledAmount(settled);
        d.setUnsettledAmount(BigDecimal.ZERO);
        d.setCreatedBy(1L);
        d.setSubmittedBy(1L);
        return d;
    }

    @Test
    void reconcile_partialPayment_marksPartial() {
        inputInvoiceMapper.insert(invoice(901L, new BigDecimal("1000.00"), "CERTIFIED", "DECLARED"));
        businessDocMapper.insert(doc(901L, new BigDecimal("600.00")));

        List<InvoiceReconcileVO> r = reconcileMapper.queryInputReconcile("202608", 1L);
        assertEquals(1, r.size());
        assertEquals("PARTIAL", r.get(0).getReconcileStatus());
        assertEquals(new BigDecimal("400.00"), r.get(0).getUnpaidAmount());
    }

    @Test
    void reconcile_fullyPaid_marksPaid() {
        inputInvoiceMapper.insert(invoice(902L, new BigDecimal("1000.00"), "CERTIFIED", "DECLARED"));
        businessDocMapper.insert(doc(902L, new BigDecimal("1000.00")));

        List<InvoiceReconcileVO> r = reconcileMapper.queryInputReconcile("202608", 1L);
        assertEquals("PAID", r.get(0).getReconcileStatus());
    }

    @Test
    void reconcile_unpaid_noDoc_marksUnpaid() {
        // 发票无关联业务单 → paidAmount=0
        InputInvoiceEntity e = invoice(903L, new BigDecimal("1000.00"), "UNCERTIFIED", "UNDECLARED");
        inputInvoiceMapper.insert(e);

        List<InvoiceReconcileVO> r = reconcileMapper.queryInputReconcile("202608", 1L);
        assertEquals(1, r.size());
        assertEquals("UNPAID", r.get(0).getReconcileStatus());
        assertEquals(new BigDecimal("1000.00"), r.get(0).getPaidAmount());
    }
}
