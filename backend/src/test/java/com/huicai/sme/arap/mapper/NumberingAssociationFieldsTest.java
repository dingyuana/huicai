package com.huicai.sme.arap.mapper;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.business.entity.InputInvoiceEntity;
import com.huicai.base.business.entity.OutputInvoiceEntity;
import com.huicai.base.business.mapper.InputInvoiceMapper;
import com.huicai.base.business.mapper.OutputInvoiceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("编号关联 - 实体字段完整性")
public class NumberingAssociationFieldsTest extends AbstractMapperTest {

    @Autowired private OutputInvoiceMapper outputInvoiceMapper;
    @Autowired private InputInvoiceMapper inputInvoiceMapper;
    @Autowired private VoucherMapper voucherMapper;
    @Autowired private BusinessDocMapper businessDocMapper;

    @Test
    @DisplayName("进项发票: docNo 和 voucherNo 字段可读写")
    void inputInvoice_docNo_voucherNo() {
        InputInvoiceEntity entity = new InputInvoiceEntity();
        entity.setInvoiceNo("9999.II.INV.001");
        entity.setInvoiceDate(LocalDate.of(2026, 6, 28));
        entity.setPeriod("202606");
        entity.setVendorId(1L);
        entity.setVendorName("测试供应商");
        entity.setAmount(new BigDecimal("10000.00"));
        entity.setTaxRate(new BigDecimal("0.13"));
        entity.setTaxAmount(new BigDecimal("1300.00"));
        entity.setTotalAmount(new BigDecimal("11300.00"));
        entity.setInvoiceType("SPECIAL");
        entity.setCertificationStatus("UNCERTIFIED");
        entity.setDocId(1L);
        entity.setDocNo("9999.DOC.INPUT.001");
        entity.setVoucherNo("9999.VCH.INPUT.001");
        entity.setDeleted(0);

        inputInvoiceMapper.insert(entity);

        InputInvoiceEntity found = inputInvoiceMapper.selectById(entity.getId());
        assertNotNull(found);
        assertEquals("9999.DOC.INPUT.001", found.getDocNo());
        assertEquals("9999.VCH.INPUT.001", found.getVoucherNo());
    }

    @Test
    @DisplayName("销项发票: docNo 和 voucherNo 字段可读写")
    void outputInvoice_docNo_voucherNo() {
        OutputInvoiceEntity entity = new OutputInvoiceEntity();
        entity.setInvoiceNo("9999.OI.INV.001");
        entity.setInvoiceDate(LocalDate.of(2026, 6, 28));
        entity.setPeriod("202606");
        entity.setCustomerId(1L);
        entity.setCustomerName("测试客户");
        entity.setAmount(new BigDecimal("10000.00"));
        entity.setTaxRate(new BigDecimal("0.13"));
        entity.setTaxAmount(new BigDecimal("1300.00"));
        entity.setTotalAmount(new BigDecimal("11300.00"));
        entity.setInvoiceType("SPECIAL");
        entity.setStatus("PENDING_CONFIRM");
        entity.setDocId(1L);
        entity.setDocNo("9999.DOC.SALE.001");
        entity.setVoucherNo("9999.VCH.SALE.001");
        entity.setDeleted(0);

        outputInvoiceMapper.insert(entity);

        OutputInvoiceEntity found = outputInvoiceMapper.selectById(entity.getId());
        assertNotNull(found);
        assertEquals("9999.DOC.SALE.001", found.getDocNo());
        assertEquals("9999.VCH.SALE.001", found.getVoucherNo());
    }

    @Test
    @DisplayName("凭证: sourceDocId, sourceDocNo, sourceDocType 三个溯源字段可读写")
    void voucher_sourceDoc_fields() {
        VoucherEntity entity = new VoucherEntity();
        entity.setVoucherNo("9999.VCH.TRACE.001");
        entity.setPeriod("202606");
        entity.setVoucherTypeId(1L);
        entity.setStatus("DRAFT");
        entity.setSource("GENERATED");
        entity.setSummary("测试溯源凭证");
        entity.setTotalDebit(new BigDecimal("11300.00"));
        entity.setTotalCredit(new BigDecimal("11300.00"));
        entity.setSourceDocId(100L);
        entity.setSourceDocNo("9999.OI.INV.001");
        entity.setSourceDocType("OUTPUT_INVOICE");
        entity.setDeleted(0);

        voucherMapper.insert(entity);

        VoucherEntity found = voucherMapper.selectById(entity.getId());
        assertNotNull(found);
        assertEquals(100L, found.getSourceDocId());
        assertEquals("9999.OI.INV.001", found.getSourceDocNo());
        assertEquals("OUTPUT_INVOICE", found.getSourceDocType());
    }

    @Test
    @DisplayName("业务单据: voucherNo 字段可读写")
    void businessDoc_voucherNo() {
        BusinessDocEntity entity = new BusinessDocEntity();
        entity.setDocNo("9999.BDOC.DOC.001");
        entity.setDocType("INVOICE_OUT");
        entity.setPeriod("202606");
        entity.setAmount(new BigDecimal("10000.00"));
        entity.setStatus("DRAFT");
        entity.setVoucherId(1L);
        entity.setVoucherNo("9999.VCH.BDOC.001");
        entity.setDeleted(0);
        entity.setDocDate(LocalDate.of(2026, 6, 28));

        businessDocMapper.insert(entity);

        BusinessDocEntity found = businessDocMapper.selectById(entity.getId());
        assertNotNull(found);
        assertEquals("9999.VCH.BDOC.001", found.getVoucherNo());
    }

    @Test
    @DisplayName("业务单据(应收): docNo, voucherNo, invoiceNo 字段可读写")
    void businessDoc_receivable_fields() {
        BusinessDocEntity entity = new BusinessDocEntity();
        entity.setDocNo("9999.REC.DOC.001");
        entity.setDocType("INVOICE_OUT");
        entity.setPeriod("202606");
        entity.setAmount(new BigDecimal("11300.00"));
        entity.setInvoiceNo("9999.OI.INV.001");
        entity.setVoucherId(1L);
        entity.setVoucherNo("9999.VCH.REC.001");
        entity.setStatus("PENDING_CONFIRM");
        entity.setCustomerId(1L);
        entity.setUnsettledAmount(new BigDecimal("11300.00"));

        businessDocMapper.insert(entity);

        BusinessDocEntity found = businessDocMapper.selectById(entity.getId());
        assertNotNull(found);
        assertEquals("9999.REC.DOC.001", found.getDocNo());
        assertEquals("9999.OI.INV.001", found.getInvoiceNo());
        assertEquals("9999.VCH.REC.001", found.getVoucherNo());
    }

    @Test
    @DisplayName("业务单据(应付): docNo, voucherNo, invoiceNo 字段可读写")
    void businessDoc_payable_fields() {
        BusinessDocEntity entity = new BusinessDocEntity();
        entity.setDocNo("9999.PAY.DOC.001");
        entity.setDocType("INVOICE_IN");
        entity.setPeriod("202606");
        entity.setAmount(new BigDecimal("11300.00"));
        entity.setInvoiceNo("9999.II.INV.001");
        entity.setVoucherId(2L);
        entity.setVoucherNo("9999.VCH.PAY.001");
        entity.setStatus("PENDING_CONFIRM");
        entity.setSupplierId(1L);
        entity.setUnsettledAmount(new BigDecimal("11300.00"));

        businessDocMapper.insert(entity);

        BusinessDocEntity found = businessDocMapper.selectById(entity.getId());
        assertNotNull(found);
        assertEquals("9999.PAY.DOC.001", found.getDocNo());
        assertEquals("9999.II.INV.001", found.getInvoiceNo());
        assertEquals("9999.VCH.PAY.001", found.getVoucherNo());
    }

    @Test
    @DisplayName("编号关联核心: 同一笔业务中发票号在业务单据上可被正确查询")
    void association_invoiceNo_query_businessDoc() {
        OutputInvoiceEntity invoice = new OutputInvoiceEntity();
        invoice.setInvoiceNo("9999.ASSOC.INV.001");
        invoice.setInvoiceDate(LocalDate.of(2026, 6, 28));
        invoice.setPeriod("202606");
        invoice.setCustomerId(1L);
        invoice.setCustomerName("测试客户");
        invoice.setAmount(new BigDecimal("10000.00"));
        invoice.setTaxRate(new BigDecimal("0.13"));
        invoice.setTaxAmount(new BigDecimal("1300.00"));
        invoice.setTotalAmount(new BigDecimal("11300.00"));
        invoice.setInvoiceType("SPECIAL");
        invoice.setStatus("CONFIRMED");
        invoice.setDeleted(0);
        outputInvoiceMapper.insert(invoice);

        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo("9999.ASSOC.REC.001");
        doc.setDocType("INVOICE_OUT");
        doc.setPeriod("202606");
        doc.setAmount(new BigDecimal("11300.00"));
        doc.setInvoiceNo("9999.ASSOC.INV.001");
        doc.setStatus("PENDING_CONFIRM");
        doc.setCustomerId(1L);
        doc.setUnsettledAmount(new BigDecimal("11300.00"));
        businessDocMapper.insert(doc);

        BusinessDocEntity found = businessDocMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BusinessDocEntity>()
                .eq(BusinessDocEntity::getInvoiceNo, "9999.ASSOC.INV.001")
        );

        assertNotNull(found);
        assertEquals("9999.ASSOC.REC.001", found.getDocNo());
    }

    @Test
    @DisplayName("编号关联核心: 凭证通过 sourceDocNo 可追溯到发票")
    void association_voucher_to_invoice_via_sourceDocNo() {
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo("9999.ASSOC.VCH.001");
        voucher.setPeriod("202606");
        voucher.setVoucherTypeId(1L);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary("关联测试凭证");
        voucher.setTotalDebit(new BigDecimal("11300.00"));
        voucher.setTotalCredit(new BigDecimal("11300.00"));
        voucher.setSourceDocId(100L);
        voucher.setSourceDocNo("9999.ASSOC.INV.001");
        voucher.setSourceDocType("OUTPUT_INVOICE");
        voucher.setDeleted(0);
        voucherMapper.insert(voucher);

        VoucherEntity found = voucherMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VoucherEntity>()
                .eq(VoucherEntity::getSourceDocNo, "9999.ASSOC.INV.001")
        );

        assertNotNull(found);
        assertEquals("9999.ASSOC.VCH.001", found.getVoucherNo());
        assertEquals("OUTPUT_INVOICE", found.getSourceDocType());
    }
}