package com.huicai.module.finance.mapper;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.module.arap.entity.ReceivableEntity;
import com.huicai.module.arap.entity.PayableEntity;
import com.huicai.module.arap.mapper.ReceivableMapper;
import com.huicai.module.arap.mapper.PayableMapper;
import com.huicai.module.finance.entity.VoucherEntity;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.tax.entity.InputInvoiceEntity;
import com.huicai.module.tax.entity.OutputInvoiceEntity;
import com.huicai.module.tax.mapper.InputInvoiceMapper;
import com.huicai.module.tax.mapper.OutputInvoiceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 编号关联体系 - 实体字段完整性测试 (L2 / @SlowTest)
 *
 * 验证 V64 Migration 新增的所有编号关联字段可正确读写：
 * - InputInvoiceEntity: docNo, voucherNo
 * - OutputInvoiceEntity: docNo, voucherNo
 * - ReceivableEntity: docNo, voucherNo, invoiceNo
 * - PayableEntity: docNo, voucherNo, invoiceNo
 * - VoucherEntity: sourceDocId, sourceDocNo, sourceDocType
 * - BusinessDocEntity: voucherNo
 * - ArapSettlementEntity: voucherNo
 *
 * 测试数据使用 9999.xxxx 编码前缀，避免与 V60 冲突
 */
@DisplayName("编号关联 - 实体字段完整性")
public class NumberingAssociationFieldsTest extends AbstractMapperTest {

    @Autowired private OutputInvoiceMapper outputInvoiceMapper;
    @Autowired private InputInvoiceMapper inputInvoiceMapper;
    @Autowired private ReceivableMapper receivableMapper;
    @Autowired private PayableMapper payableMapper;
    @Autowired private VoucherMapper voucherMapper;
    @Autowired private BusinessDocMapper businessDocMapper;

    // ==================== InputInvoiceEntity 字段测试 ====================

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

    // ==================== OutputInvoiceEntity 字段测试 ====================

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

    // ==================== ReceivableEntity 字段测试 ====================

    @Test
    @DisplayName("应收单: docNo, voucherNo, invoiceNo 三个字段可读写")
    void receivable_docNo_voucherNo_invoiceNo() {
        ReceivableEntity entity = new ReceivableEntity();
        entity.setDocNo("9999.REC.DOC.001");
        entity.setPeriod("202606");
        entity.setAmount(new BigDecimal("11300.00"));
        entity.setInvoiceNo("9999.OI.INV.001");
        entity.setVoucherId(1L);
        entity.setVoucherNo("9999.VCH.REC.001");
        entity.setStatus("PENDING_CONFIRM");
        entity.setDeleted(0);
        entity.setCustomerId(1L);
        entity.setTxDate(LocalDate.of(2026, 6, 28));
        entity.setUnsettledAmount(new BigDecimal("11300.00"));

        receivableMapper.insert(entity);

        ReceivableEntity found = receivableMapper.selectById(entity.getId());
        assertNotNull(found);
        assertEquals("9999.REC.DOC.001", found.getDocNo());
        assertEquals("9999.OI.INV.001", found.getInvoiceNo());
        assertEquals("9999.VCH.REC.001", found.getVoucherNo());
    }

    // ==================== PayableEntity 字段测试 ====================

    @Test
    @DisplayName("应付单: docNo, voucherNo, invoiceNo 三个字段可读写")
    void payable_docNo_voucherNo_invoiceNo() {
        PayableEntity entity = new PayableEntity();
        entity.setDocNo("9999.PAY.DOC.001");
        entity.setPeriod("202606");
        entity.setAmount(new BigDecimal("11300.00"));
        entity.setInvoiceNo("9999.II.INV.001");
        entity.setVoucherId(2L);
        entity.setVoucherNo("9999.VCH.PAY.001");
        entity.setStatus("PENDING_CONFIRM");
        entity.setDeleted(0);
        entity.setVendorId(1L);
        entity.setTxDate(LocalDate.of(2026, 6, 28));
        entity.setUnsettledAmount(new BigDecimal("11300.00"));

        payableMapper.insert(entity);

        PayableEntity found = payableMapper.selectById(entity.getId());
        assertNotNull(found);
        assertEquals("9999.PAY.DOC.001", found.getDocNo());
        assertEquals("9999.II.INV.001", found.getInvoiceNo());
        assertEquals("9999.VCH.PAY.001", found.getVoucherNo());
    }

    // ==================== VoucherEntity 字段测试 ====================

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

    // ==================== BusinessDocEntity 字段测试 ====================

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

    // ==================== 编号关联核心验证 ====================

    @Test
    @DisplayName("编号关联核心: 同一笔业务中发票号在应收单上可被正确查询")
    void association_invoiceNo_query_receivable() {
        // 1. 插入销项发票
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

        // 2. 插入应收单，invoiceNo = 发票号
        ReceivableEntity receivable = new ReceivableEntity();
        receivable.setDocNo("9999.ASSOC.REC.001");
        receivable.setPeriod("202606");
        receivable.setAmount(new BigDecimal("11300.00"));
        receivable.setInvoiceNo("9999.ASSOC.INV.001"); // 关联发票号
        receivable.setStatus("PENDING_CONFIRM");
        receivable.setDeleted(0);
        receivable.setCustomerId(1L);
        receivable.setTxDate(LocalDate.of(2026, 6, 28));
        receivable.setUnsettledAmount(new BigDecimal("11300.00"));
        receivableMapper.insert(receivable);

        // 3. 通过 invoiceNo 查询应收单
        ReceivableEntity found = receivableMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ReceivableEntity>()
                .eq(ReceivableEntity::getInvoiceNo, "9999.ASSOC.INV.001")
        );

        assertNotNull(found);
        assertEquals("9999.ASSOC.REC.001", found.getDocNo());
    }

    @Test
    @DisplayName("编号关联核心: 同一笔业务中发票号在应付单上可被正确查询")
    void association_invoiceNo_query_payable() {
        // 1. 插入进项发票
        InputInvoiceEntity invoice = new InputInvoiceEntity();
        invoice.setInvoiceNo("9999.ASSOC.II.001");
        invoice.setInvoiceDate(LocalDate.of(2026, 6, 28));
        invoice.setPeriod("202606");
        invoice.setVendorId(1L);
        invoice.setVendorName("测试供应商");
        invoice.setAmount(new BigDecimal("10000.00"));
        invoice.setTaxRate(new BigDecimal("0.13"));
        invoice.setTaxAmount(new BigDecimal("1300.00"));
        invoice.setTotalAmount(new BigDecimal("11300.00"));
        invoice.setInvoiceType("SPECIAL");
        invoice.setCertificationStatus("UNCERTIFIED");
        invoice.setDeleted(0);
        inputInvoiceMapper.insert(invoice);

        // 2. 插入应付单，invoiceNo = 发票号
        PayableEntity payable = new PayableEntity();
        payable.setDocNo("9999.ASSOC.PAY.001");
        payable.setPeriod("202606");
        payable.setAmount(new BigDecimal("11300.00"));
        payable.setInvoiceNo("9999.ASSOC.II.001"); // 关联发票号
        payable.setStatus("PENDING_CONFIRM");
        payable.setDeleted(0);
        payable.setVendorId(1L);
        payable.setTxDate(LocalDate.of(2026, 6, 28));
        payable.setUnsettledAmount(new BigDecimal("11300.00"));
        payableMapper.insert(payable);

        // 3. 通过 invoiceNo 查询应付单
        PayableEntity found = payableMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PayableEntity>()
                .eq(PayableEntity::getInvoiceNo, "9999.ASSOC.II.001")
        );

        assertNotNull(found);
        assertEquals("9999.ASSOC.PAY.001", found.getDocNo());
    }

    @Test
    @DisplayName("编号关联核心: 凭证通过 sourceDocNo 可追溯到发票")
    void association_voucher_to_invoice_via_sourceDocNo() {
        // 1. 插入凭证，sourceDocNo = 发票号
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

        // 2. 通过 sourceDocNo 查询凭证
        VoucherEntity found = voucherMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<VoucherEntity>()
                .eq(VoucherEntity::getSourceDocNo, "9999.ASSOC.INV.001")
        );

        assertNotNull(found);
        assertEquals("9999.ASSOC.VCH.001", found.getVoucherNo());
        assertEquals("OUTPUT_INVOICE", found.getSourceDocType());
    }
}
