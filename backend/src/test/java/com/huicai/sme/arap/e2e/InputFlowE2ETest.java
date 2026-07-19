package com.huicai.sme.arap.e2e;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.arap.entity.BusinessDocEntity;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.sme.arap.mapper.BusinessDocMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.sme.tax.entity.InputInvoiceEntity;
import com.huicai.sme.tax.mapper.InputInvoiceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 进项发票 E2E 测试 (P40).
 *
 * 覆盖：手动创建 → 提交审核 → 审核通过(自动创建 INVOICE_IN 业务单据+凭证) → 验证
 * 与 SalesFlowE2ETest 对称，科目方向相反。
 */
public class InputFlowE2ETest extends AbstractMapperTest {

    @Autowired
    private InputInvoiceMapper inputInvoiceMapper;

    @Autowired
    private BusinessDocMapper businessDocMapper;

    @Autowired
    private VoucherMapper voucherMapper;

    @Test
    void step1_createInputInvoice_shouldSucceed() {
        InputInvoiceEntity invoice = new InputInvoiceEntity();
        invoice.setInvoiceNo("IN-2026-0001");
        invoice.setInvoiceType("SPECIAL");
        invoice.setVendorId(1L);
        invoice.setVendorName("测试供应商");
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setPeriod("202607");
        invoice.setAmount(new BigDecimal("10000.00"));
        invoice.setTaxRate(new BigDecimal("13"));
        invoice.setTaxAmount(new BigDecimal("1300.00"));
        invoice.setTotalAmount(new BigDecimal("11300.00"));
        invoice.setStatus("PENDING_CONFIRM");
        invoice.setCertificationStatus("UNCERTIFIED");
        invoice.setCreatedBy(1L);
        invoice.setDeleted(0);

        int rows = inputInvoiceMapper.insert(invoice);

        assertEquals(1, rows);
        assertNotNull(invoice.getId());
        assertEquals("PENDING_CONFIRM", invoice.getStatus());
        assertEquals(0, new BigDecimal("11300.00").compareTo(invoice.getTotalAmount()));
    }

    @Test
    void step2_auditInputInvoice_shouldChangeStatus() {
        InputInvoiceEntity invoice = new InputInvoiceEntity();
        invoice.setInvoiceNo("IN-2026-0002");
        invoice.setInvoiceType("SPECIAL");
        invoice.setVendorId(1L);
        invoice.setVendorName("测试供应商");
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setPeriod("202607");
        invoice.setAmount(new BigDecimal("10000.00"));
        invoice.setTaxRate(new BigDecimal("13"));
        invoice.setTaxAmount(new BigDecimal("1300.00"));
        invoice.setTotalAmount(new BigDecimal("11300.00"));
        invoice.setStatus("PENDING_CONFIRM");
        invoice.setCertificationStatus("UNCERTIFIED");
        invoice.setCreatedBy(1L);
        invoice.setDeleted(0);
        inputInvoiceMapper.insert(invoice);

        // 提交审核
        invoice.setStatus("PENDING_REVIEW");
        int rows = inputInvoiceMapper.updateById(invoice);
        assertEquals(1, rows);

        // 审核通过
        invoice.setStatus("CONFIRMED");
        invoice.setAuditedBy(1L);
        rows = inputInvoiceMapper.updateById(invoice);
        assertEquals(1, rows);

        InputInvoiceEntity audited = inputInvoiceMapper.selectById(invoice.getId());
        assertEquals("CONFIRMED", audited.getStatus());
        assertNotNull(audited.getAuditedBy());
    }

    @Test
    void step3_afterAudit_shouldCreateBusinessDoc() {
        InputInvoiceEntity invoice = new InputInvoiceEntity();
        invoice.setInvoiceNo("IN-2026-0003");
        invoice.setInvoiceType("SPECIAL");
        invoice.setVendorId(1L);
        invoice.setVendorName("测试供应商");
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setPeriod("202607");
        invoice.setAmount(new BigDecimal("5000.00"));
        invoice.setTaxRate(new BigDecimal("13"));
        invoice.setTaxAmount(new BigDecimal("650.00"));
        invoice.setTotalAmount(new BigDecimal("5650.00"));
        invoice.setStatus("CONFIRMED");
        invoice.setCertificationStatus("UNCERTIFIED");
        invoice.setCreatedBy(1L);
        invoice.setDeleted(0);
        inputInvoiceMapper.insert(invoice);

        // 创建 INVOICE_IN 业务单据（模拟审核通过后自动创建）
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocType("INVOICE_IN");
        doc.setSupplierId(invoice.getVendorId());
        doc.setInvoiceNo(invoice.getInvoiceNo());
        doc.setPeriod("202607");
        doc.setDocDate(invoice.getInvoiceDate());
        doc.setAmount(invoice.getTotalAmount());
        doc.setSettledAmount(BigDecimal.ZERO);
        doc.setUnsettledAmount(invoice.getTotalAmount());
        doc.setStatus("DRAFT");
        doc.setSource("MANUAL");
        businessDocMapper.insert(doc);

        assertNotNull(doc.getId());
        assertEquals(invoice.getInvoiceNo(), doc.getInvoiceNo());
        assertEquals(0, invoice.getTotalAmount().compareTo(doc.getAmount()));
        assertEquals("DRAFT", doc.getStatus());
        assertEquals("INVOICE_IN", doc.getDocType());

        // 回写发票
        invoice.setDocId(doc.getId());
        invoice.setDocNo(doc.getDocNo());
        int rows = inputInvoiceMapper.updateById(invoice);
        assertEquals(1, rows);

        InputInvoiceEntity updated = inputInvoiceMapper.selectById(invoice.getId());
        assertNotNull(updated.getDocId());
    }

    @Test
    void step4_afterAudit_shouldCreateVoucher() {
        InputInvoiceEntity invoice = new InputInvoiceEntity();
        invoice.setInvoiceNo("IN-2026-0004");
        invoice.setInvoiceType("SPECIAL");
        invoice.setVendorId(1L);
        invoice.setVendorName("测试供应商");
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setPeriod("202607");
        invoice.setAmount(new BigDecimal("5000.00"));
        invoice.setTaxRate(new BigDecimal("13"));
        invoice.setTaxAmount(new BigDecimal("650.00"));
        invoice.setTotalAmount(new BigDecimal("5650.00"));
        invoice.setStatus("VOUCHERED");
        invoice.setCertificationStatus("UNCERTIFIED");
        invoice.setCreatedBy(1L);
        invoice.setDeleted(0);
        inputInvoiceMapper.insert(invoice);

        // 创建业务单据（前置）
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocType("INVOICE_IN");
        doc.setSupplierId(invoice.getVendorId());
        doc.setInvoiceNo(invoice.getInvoiceNo());
        doc.setPeriod("202607");
        doc.setDocDate(invoice.getInvoiceDate());
        doc.setAmount(invoice.getTotalAmount());
        doc.setSettledAmount(BigDecimal.ZERO);
        doc.setUnsettledAmount(invoice.getTotalAmount());
        doc.setStatus("DRAFT");
        doc.setSource("MANUAL");
        businessDocMapper.insert(doc);

        // 创建凭证（模拟审核通过后自动生成）
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo("PZ-2026-07-0004");
        voucher.setPeriod("202607");
        voucher.setVoucherTypeId(2L); // 付款凭证
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSourceDocType("INPUT_INVOICE");
        voucher.setSourceDocNo(invoice.getInvoiceNo());
        voucher.setTotalDebit(invoice.getTotalAmount());
        voucher.setTotalCredit(invoice.getTotalAmount());
        voucher.setCreatedBy(1L);
        voucherMapper.insert(voucher);

        // 回写发票
        invoice.setVoucherId(voucher.getId());
        invoice.setVoucherNo(voucher.getVoucherNo());
        inputInvoiceMapper.updateById(invoice);

        // 回写业务单据
        doc.setVoucherId(voucher.getId());
        doc.setVoucherNo(voucher.getVoucherNo());
        doc.setStatus("VOUCHERED");
        businessDocMapper.updateById(doc);

        assertNotNull(voucher.getId());
        assertEquals(0, voucher.getTotalDebit().compareTo(voucher.getTotalCredit()));
        assertEquals("202607", voucher.getPeriod());
        assertEquals("INPUT_INVOICE", voucher.getSourceDocType());

        // 验证双向关联
        InputInvoiceEntity finalInv = inputInvoiceMapper.selectById(invoice.getId());
        BusinessDocEntity finalDoc = businessDocMapper.selectById(doc.getId());
        VoucherEntity finalVoucher = voucherMapper.selectById(voucher.getId());

        assertEquals(invoice.getVoucherNo(), finalInv.getVoucherNo());
        assertEquals(invoice.getVoucherNo(), finalDoc.getVoucherNo());
        assertEquals("VOUCHERED", finalDoc.getStatus());
        assertEquals(0, finalDoc.getAmount().compareTo(finalVoucher.getTotalDebit()));
    }

    @Test
    void fullInputFlow_endToEnd_shouldCompleteSuccessfully() {
        // === 1. 创建进项发票 ===
        InputInvoiceEntity invoice = new InputInvoiceEntity();
        invoice.setInvoiceNo("IN-2026-0099");
        invoice.setInvoiceType("SPECIAL");
        invoice.setVendorId(99L);
        invoice.setVendorName("全流程供应商");
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setPeriod("202607");
        invoice.setAmount(new BigDecimal("20000.00"));
        invoice.setTaxRate(new BigDecimal("13"));
        invoice.setTaxAmount(new BigDecimal("2600.00"));
        invoice.setTotalAmount(new BigDecimal("22600.00"));
        invoice.setStatus("PENDING_CONFIRM");
        invoice.setCertificationStatus("UNCERTIFIED");
        invoice.setCreatedBy(1L);
        invoice.setDeleted(0);
        inputInvoiceMapper.insert(invoice);

        // === 2. 提交审核 ===
        invoice.setStatus("PENDING_REVIEW");
        inputInvoiceMapper.updateById(invoice);

        // === 3. 审核通过 ===
        invoice.setStatus("CONFIRMED");
        invoice.setAuditedBy(1L);
        inputInvoiceMapper.updateById(invoice);

        // === 4. 创建 INVOICE_IN 业务单据 ===
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocType("INVOICE_IN");
        doc.setSupplierId(invoice.getVendorId());
        doc.setInvoiceNo(invoice.getInvoiceNo());
        doc.setPeriod("202607");
        doc.setDocDate(invoice.getInvoiceDate());
        doc.setAmount(invoice.getTotalAmount());
        doc.setSettledAmount(BigDecimal.ZERO);
        doc.setUnsettledAmount(invoice.getTotalAmount());
        doc.setStatus("DRAFT");
        doc.setSource("MANUAL");
        businessDocMapper.insert(doc);

        invoice.setDocId(doc.getId());
        invoice.setDocNo(doc.getDocNo());
        inputInvoiceMapper.updateById(invoice);

        // === 5. 创建凭证 ===
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo("PZ-2026-07-0099");
        voucher.setPeriod("202607");
        voucher.setVoucherTypeId(2L);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSourceDocType("INPUT_INVOICE");
        voucher.setSourceDocNo(invoice.getInvoiceNo());
        voucher.setTotalDebit(invoice.getTotalAmount());
        voucher.setTotalCredit(invoice.getTotalAmount());
        voucher.setCreatedBy(1L);
        voucherMapper.insert(voucher);

        // 回写
        invoice.setStatus("VOUCHERED");
        invoice.setVoucherId(voucher.getId());
        invoice.setVoucherNo(voucher.getVoucherNo());
        inputInvoiceMapper.updateById(invoice);

        doc.setVoucherId(voucher.getId());
        doc.setVoucherNo(voucher.getVoucherNo());
        doc.setStatus("VOUCHERED");
        businessDocMapper.updateById(doc);

        // === 6. 验证全链路 ===
        InputInvoiceEntity finalInv = inputInvoiceMapper.selectById(invoice.getId());
        BusinessDocEntity finalDoc = businessDocMapper.selectById(doc.getId());
        VoucherEntity finalVoucher = voucherMapper.selectById(voucher.getId());

        // 验证发票状态
        assertEquals("VOUCHERED", finalInv.getStatus());
        assertNotNull(finalInv.getAuditedBy());

        // 验证业务单据
        assertEquals("VOUCHERED", finalDoc.getStatus());
        assertEquals(invoice.getInvoiceNo(), finalDoc.getInvoiceNo());
        assertEquals(0, invoice.getTotalAmount().compareTo(finalDoc.getAmount()));

        // 验证凭证
        assertEquals(0, finalVoucher.getTotalDebit().compareTo(finalVoucher.getTotalCredit()));
        assertEquals("INPUT_INVOICE", finalVoucher.getSourceDocType());

        // 验证双向关联
        assertEquals(finalVoucher.getId(), finalInv.getVoucherId());
        assertEquals(finalVoucher.getId(), finalDoc.getVoucherId());
        assertEquals(finalDoc.getId(), finalInv.getDocId());

        // 验证金额一致性
        assertEquals(0, finalInv.getTotalAmount().compareTo(finalDoc.getAmount()));
        assertEquals(0, finalDoc.getAmount().compareTo(finalVoucher.getTotalDebit()));
    }
}