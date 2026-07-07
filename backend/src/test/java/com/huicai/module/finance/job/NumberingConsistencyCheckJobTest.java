package com.huicai.module.finance.job;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.common.test.SlowTest;
import com.huicai.module.arap.entity.ArapSettlementEntity;
import com.huicai.module.arap.mapper.ArapSettlementMapper;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.entity.VoucherEntity;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.tax.entity.InputInvoiceEntity;
import com.huicai.module.tax.entity.OutputInvoiceEntity;
import com.huicai.module.tax.mapper.InputInvoiceMapper;
import com.huicai.module.tax.mapper.OutputInvoiceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SlowTest
@DisplayName("编号关联 - 一致性校验 Job 测试")
public class NumberingConsistencyCheckJobTest extends AbstractMapperTest {

    @Autowired private NumberingConsistencyCheckJob job;
    @Autowired private OutputInvoiceMapper outputInvoiceMapper;
    @Autowired private InputInvoiceMapper inputInvoiceMapper;
    @Autowired private VoucherMapper voucherMapper;
    @Autowired private BusinessDocMapper businessDocMapper;
    @Autowired private ArapSettlementMapper settlementMapper;

    @Test
    @DisplayName("无问题场景: 所有关联字段一致")
    void clean_data_no_issues() {
        OutputInvoiceEntity invoice = new OutputInvoiceEntity();
        invoice.setInvoiceNo("9999.JOB.CLEAN.INV.001");
        invoice.setInvoiceDate(LocalDate.of(2026, 6, 28));
        invoice.setPeriod("202606");
        invoice.setAmount(new BigDecimal("10000.00"));
        invoice.setTaxRate(new BigDecimal("0.13"));
        invoice.setTaxAmount(new BigDecimal("1300.00"));
        invoice.setTotalAmount(new BigDecimal("11300.00"));
        invoice.setInvoiceType("SPECIAL");
        invoice.setStatus("CONFIRMED");
        invoice.setDocNo("9999.JOB.CLEAN.DOC.001");
        invoice.setVoucherNo("9999.JOB.CLEAN.VCH.001");
        invoice.setDeleted(0);
        outputInvoiceMapper.insert(invoice);

        // P34: 使用业务单据替代应收单
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo("9999.JOB.CLEAN.DOC.001");
        doc.setDocType("INVOICE_OUT");
        doc.setPeriod("202606");
        doc.setAmount(new BigDecimal("11300.00"));
        doc.setInvoiceNo("9999.JOB.CLEAN.INV.001");
        doc.setStatus("CONFIRMED");
        doc.setCustomerId(1L);
        doc.setUnsettledAmount(new BigDecimal("11300.00"));
        businessDocMapper.insert(doc);

        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo("9999.JOB.CLEAN.VCH.001");
        voucher.setPeriod("202606");
        voucher.setVoucherTypeId(1L);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary("一致性测试凭证");
        voucher.setTotalDebit(new BigDecimal("11300.00"));
        voucher.setTotalCredit(new BigDecimal("11300.00"));
        voucher.setSourceDocId(1L);
        voucher.setSourceDocNo("9999.JOB.CLEAN.INV.001");
        voucher.setSourceDocType("OUTPUT_INVOICE");
        voucher.setDeleted(0);
        voucherMapper.insert(voucher);

        assertDoesNotThrow(() -> job.execute());
    }

    @Test
    @DisplayName("脏数据: 销项发票有 docId 但无 docNo")
    void dirty_invoice_has_docId_no_docNo() {
        OutputInvoiceEntity invoice = new OutputInvoiceEntity();
        invoice.setInvoiceNo("9999.JOB.DIRTY.INV.001");
        invoice.setInvoiceDate(LocalDate.of(2026, 6, 28));
        invoice.setPeriod("202606");
        invoice.setAmount(new BigDecimal("10000.00"));
        invoice.setTaxRate(new BigDecimal("0.13"));
        invoice.setTaxAmount(new BigDecimal("1300.00"));
        invoice.setTotalAmount(new BigDecimal("11300.00"));
        invoice.setInvoiceType("SPECIAL");
        invoice.setStatus("CONFIRMED");
        invoice.setDocId(1L);
        invoice.setDocNo(null);
        invoice.setVoucherNo("9999.JOB.DIRTY.VCH.001");
        invoice.setDeleted(0);
        outputInvoiceMapper.insert(invoice);

        assertDoesNotThrow(() -> job.execute());
    }

    @Test
    @DisplayName("脏数据: 凭证有 sourceDocId 但无 sourceDocNo")
    void dirty_voucher_has_sourceDocId_no_sourceDocNo() {
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo("9999.JOB.DIRTY.VCH.002");
        voucher.setPeriod("202606");
        voucher.setVoucherTypeId(1L);
        voucher.setStatus("DRAFT");
        voucher.setSource("MANUAL");
        voucher.setSummary("脏数据测试");
        voucher.setTotalDebit(new BigDecimal("1000.00"));
        voucher.setTotalCredit(new BigDecimal("1000.00"));
        voucher.setSourceDocId(99L);
        voucher.setSourceDocNo(null);
        voucher.setSourceDocType("BUSINESS_DOC");
        voucher.setDeleted(0);
        voucherMapper.insert(voucher);

        assertDoesNotThrow(() -> job.execute());
    }

    @Test
    @DisplayName("脏数据: 业务单据有 voucherId 但无 voucherNo")
    void dirty_business_doc_has_voucherId_no_voucherNo() {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo("9999.JOB.DIRTY.DOC.001");
        doc.setDocType("INVOICE_OUT");
        doc.setPeriod("202606");
        doc.setAmount(new BigDecimal("5000.00"));
        doc.setStatus("VOUCHERED");
        doc.setCustomerId(1L);
        doc.setVoucherId(100L);
        doc.setVoucherNo(null);
        businessDocMapper.insert(doc);

        assertDoesNotThrow(() -> job.execute());
    }

    @Test
    @DisplayName("脏数据: 核销单有 voucherId 但无 voucherNo")
    void dirty_settlement_has_voucherId_no_voucherNo() {
        ArapSettlementEntity settlement = new ArapSettlementEntity();
        settlement.setSettlementNo("9999.JOB.DIRTY.SETTLE.001");
        settlement.setSettlementDate(LocalDate.of(2026, 6, 28));
        settlement.setPeriod("202606");
        settlement.setPartyId(1L);
        settlement.setPartyType("CUSTOMER");
        settlement.setTotalAmount(new BigDecimal("10000.00"));
        settlement.setStatus("DRAFT");
        settlement.setVoucherId(50L);
        settlement.setVoucherNo(null);
        settlementMapper.insert(settlement);

        assertDoesNotThrow(() -> job.execute());
    }

    @Test
    @DisplayName("混合脏数据: 多种不一致同时存在")
    void mixed_dirty_data() {
        OutputInvoiceEntity inv1 = new OutputInvoiceEntity();
        inv1.setInvoiceNo("9999.JOB.MIX.INV.001");
        inv1.setInvoiceDate(LocalDate.of(2026, 6, 28));
        inv1.setPeriod("202606");
        inv1.setAmount(new BigDecimal("10000.00"));
        inv1.setTaxRate(new BigDecimal("0.13"));
        inv1.setTaxAmount(new BigDecimal("1300.00"));
        inv1.setTotalAmount(new BigDecimal("11300.00"));
        inv1.setInvoiceType("SPECIAL");
        inv1.setStatus("CONFIRMED");
        inv1.setDocNo(null);
        inv1.setVoucherNo("9999.JOB.MIX.VCH.001");
        inv1.setDeleted(0);
        outputInvoiceMapper.insert(inv1);

        // P34: 使用业务单据替代应收单
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo("9999.JOB.MIX.DOC.001");
        doc.setDocType("INVOICE_OUT");
        doc.setPeriod("202606");
        doc.setAmount(new BigDecimal("11300.00"));
        doc.setInvoiceNo("9999.JOB.MIX.INV.002");
        doc.setStatus("CONFIRMED");
        doc.setCustomerId(1L);
        doc.setUnsettledAmount(new BigDecimal("11300.00"));
        businessDocMapper.insert(doc);

        VoucherEntity vch = new VoucherEntity();
        vch.setVoucherNo("9999.JOB.MIX.VCH.001");
        vch.setPeriod("202606");
        vch.setVoucherTypeId(1L);
        vch.setStatus("DRAFT");
        vch.setSource("GENERATED");
        vch.setSummary("混合测试");
        vch.setTotalDebit(new BigDecimal("11300.00"));
        vch.setTotalCredit(new BigDecimal("11300.00"));
        vch.setSourceDocId(70L);
        vch.setSourceDocNo(null);
        vch.setSourceDocType("OUTPUT_INVOICE");
        vch.setDeleted(0);
        voucherMapper.insert(vch);

        assertDoesNotThrow(() -> job.execute());
    }
}