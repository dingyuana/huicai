package com.huicai.sme.arap.e2e;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.business.entity.OutputInvoiceEntity;
import com.huicai.base.business.mapper.OutputInvoiceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class SalesFlowE2ETest extends AbstractMapperTest {

    @Autowired
    private OutputInvoiceMapper outputInvoiceMapper;
    
    @Autowired
    private BusinessDocMapper businessDocMapper;
    
    @Autowired
    private VoucherMapper voucherMapper;

    @Test
    void step1_createSalesInvoice_shouldSucceed() {
        OutputInvoiceEntity invoice = new OutputInvoiceEntity();
        invoice.setInvoiceNo("XS-2026-0001");
        invoice.setInvoiceType("SPECIAL");
        invoice.setCustomerId(1L);
        invoice.setCustomerName("测试客户");
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setPeriod("202606");
        invoice.setAmount(new BigDecimal("10000.00"));
        invoice.setTaxRate(new BigDecimal("0.13"));
        invoice.setTaxAmount(new BigDecimal("1300.00"));
        invoice.setTotalAmount(new BigDecimal("11300.00"));
        invoice.setStatus("PENDING_CONFIRM");
        invoice.setCreatedBy(1L);
        invoice.setDeleted(0);

        int rows = outputInvoiceMapper.insert(invoice);

        assertEquals(1, rows);
        assertNotNull(invoice.getId());
        assertEquals("PENDING_CONFIRM", invoice.getStatus());
        assertEquals(0, new BigDecimal("11300.00").compareTo(invoice.getTotalAmount()));
    }

    @Test
    void step2_auditSalesInvoice_shouldChangeStatus() {
        OutputInvoiceEntity invoice = new OutputInvoiceEntity();
        invoice.setInvoiceNo("XS-2026-0002");
        invoice.setInvoiceType("SPECIAL");
        invoice.setCustomerId(1L);
        invoice.setCustomerName("测试客户");
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setPeriod("202606");
        invoice.setAmount(new BigDecimal("10000.00"));
        invoice.setTaxRate(new BigDecimal("0.13"));
        invoice.setTaxAmount(new BigDecimal("1300.00"));
        invoice.setTotalAmount(new BigDecimal("11300.00"));
        invoice.setStatus("PENDING_CONFIRM");
        invoice.setCreatedBy(1L);
        invoice.setDeleted(0);
        outputInvoiceMapper.insert(invoice);

        invoice.setStatus("CONFIRMED");
        int rows = outputInvoiceMapper.updateById(invoice);

        assertEquals(1, rows);
        OutputInvoiceEntity audited = outputInvoiceMapper.selectById(invoice.getId());
        assertEquals("CONFIRMED", audited.getStatus());
    }

    @Test
    void step3_afterAudit_shouldCreateBusinessDoc() {
        OutputInvoiceEntity invoice = new OutputInvoiceEntity();
        invoice.setInvoiceNo("XS-2026-0003");
        invoice.setInvoiceType("SPECIAL");
        invoice.setCustomerId(1L);
        invoice.setCustomerName("测试客户");
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setPeriod("202606");
        invoice.setAmount(new BigDecimal("5000.00"));
        invoice.setTaxRate(new BigDecimal("0.13"));
        invoice.setTaxAmount(new BigDecimal("650.00"));
        invoice.setTotalAmount(new BigDecimal("5650.00"));
        invoice.setStatus("CONFIRMED");
        invoice.setCreatedBy(1L);
        invoice.setDeleted(0);
        outputInvoiceMapper.insert(invoice);

        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocType("INVOICE_OUT");
        doc.setCustomerId(invoice.getCustomerId());
        doc.setInvoiceNo(invoice.getInvoiceNo());
        doc.setPeriod("202606");
        doc.setDocDate(invoice.getInvoiceDate());
        doc.setAmount(invoice.getTotalAmount());
        doc.setSettledAmount(BigDecimal.ZERO);
        doc.setUnsettledAmount(invoice.getTotalAmount());
        doc.setStatus("DRAFT");
        businessDocMapper.insert(doc);

        assertNotNull(doc.getId());
        assertEquals(invoice.getInvoiceNo(), doc.getInvoiceNo());
        assertEquals(0, invoice.getTotalAmount().compareTo(doc.getAmount()));
        assertEquals("DRAFT", doc.getStatus());
    }

    @Test
    void step4_settleBusinessDoc_shouldUpdateStatusAndAmount() {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocType("INVOICE_OUT");
        doc.setCustomerId(1L);
        doc.setPeriod("202606");
        doc.setDocDate(LocalDate.now());
        doc.setAmount(new BigDecimal("5650.00"));
        doc.setSettledAmount(BigDecimal.ZERO);
        doc.setUnsettledAmount(new BigDecimal("5650.00"));
        doc.setStatus("CONFIRMED");
        businessDocMapper.insert(doc);

        doc.setSettledAmount(new BigDecimal("5650.00"));
        doc.setUnsettledAmount(BigDecimal.ZERO);
        int rows = businessDocMapper.updateById(doc);

        assertEquals(1, rows);
        BusinessDocEntity settled = businessDocMapper.selectById(doc.getId());
        assertEquals(0, new BigDecimal("5650.00").compareTo(settled.getSettledAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(settled.getUnsettledAmount()));
    }

    @Test
    void step5_afterSettlement_shouldCreateVoucher() {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocType("INVOICE_OUT");
        doc.setCustomerId(1L);
        doc.setPeriod("202606");
        doc.setDocDate(LocalDate.now());
        doc.setAmount(new BigDecimal("5650.00"));
        doc.setSettledAmount(new BigDecimal("5650.00"));
        doc.setUnsettledAmount(BigDecimal.ZERO);
        doc.setStatus("SETTLED");
        businessDocMapper.insert(doc);

        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo("PZ-2026-06-0001");
        voucher.setPeriod("202606");
        voucher.setVoucherTypeId(1L);
        voucher.setStatus("DRAFT");
        voucher.setTotalDebit(new BigDecimal("5650.00"));
        voucher.setTotalCredit(new BigDecimal("5650.00"));
        voucher.setCreatedBy(1L);
        voucherMapper.insert(voucher);

        assertNotNull(voucher.getId());
        assertEquals(0, voucher.getTotalDebit().compareTo(voucher.getTotalCredit()));
        assertEquals("202606", voucher.getPeriod());
    }

    @Test
    void fullSalesFlow_endToEnd_shouldCompleteSuccessfully() {
        OutputInvoiceEntity invoice = new OutputInvoiceEntity();
        invoice.setInvoiceNo("XS-2026-0099");
        invoice.setInvoiceType("SPECIAL");
        invoice.setCustomerId(99L);
        invoice.setCustomerName("全流程测试客户");
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setPeriod("202606");
        invoice.setAmount(new BigDecimal("20000.00"));
        invoice.setTaxRate(new BigDecimal("0.13"));
        invoice.setTaxAmount(new BigDecimal("2600.00"));
        invoice.setTotalAmount(new BigDecimal("22600.00"));
        invoice.setStatus("PENDING_CONFIRM");
        invoice.setCreatedBy(1L);
        invoice.setDeleted(0);
        outputInvoiceMapper.insert(invoice);
        
        invoice.setStatus("CONFIRMED");
        outputInvoiceMapper.updateById(invoice);

        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocType("INVOICE_OUT");
        doc.setCustomerId(invoice.getCustomerId());
        doc.setInvoiceNo(invoice.getInvoiceNo());
        doc.setPeriod("202606");
        doc.setDocDate(invoice.getInvoiceDate());
        doc.setAmount(invoice.getTotalAmount());
        doc.setSettledAmount(BigDecimal.ZERO);
        doc.setUnsettledAmount(invoice.getTotalAmount());
        doc.setStatus("CONFIRMED");
        businessDocMapper.insert(doc);

        doc.setSettledAmount(invoice.getTotalAmount());
        doc.setUnsettledAmount(BigDecimal.ZERO);
        businessDocMapper.updateById(doc);

        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo("PZ-2026-06-0099");
        voucher.setPeriod("202606");
        voucher.setVoucherTypeId(1L);
        voucher.setStatus("DRAFT");
        voucher.setTotalDebit(invoice.getTotalAmount());
        voucher.setTotalCredit(invoice.getTotalAmount());
        voucher.setCreatedBy(1L);
        voucherMapper.insert(voucher);

        OutputInvoiceEntity finalInvoice = outputInvoiceMapper.selectById(invoice.getId());
        BusinessDocEntity finalDoc = businessDocMapper.selectById(doc.getId());
        VoucherEntity finalVoucher = voucherMapper.selectById(voucher.getId());

        assertEquals("CONFIRMED", finalInvoice.getStatus());
        
        assertEquals(0, finalInvoice.getTotalAmount().compareTo(finalDoc.getAmount()));
        assertEquals(0, finalDoc.getSettledAmount().compareTo(finalVoucher.getTotalDebit()));
        assertEquals(0, finalVoucher.getTotalDebit().compareTo(finalVoucher.getTotalCredit()));
        
        assertNotNull(finalInvoice);
        assertNotNull(finalDoc);
        assertNotNull(finalVoucher);
    }
}