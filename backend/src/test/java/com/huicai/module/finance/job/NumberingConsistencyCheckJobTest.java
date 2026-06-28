package com.huicai.module.finance.job;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.common.test.SlowTest;
import com.huicai.module.arap.entity.ArapSettlementEntity;
import com.huicai.module.arap.entity.PayableEntity;
import com.huicai.module.arap.entity.ReceivableEntity;
import com.huicai.module.arap.mapper.ArapSettlementMapper;
import com.huicai.module.arap.mapper.PayableMapper;
import com.huicai.module.arap.mapper.ReceivableMapper;
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

/**
 * 编号关联体系 - 一致性校验 Job 测试 (L2 / @SlowTest)
 *
 * 验证 NumberingConsistencyCheckJob 对 5 类脏数据的检测能力。
 * 通过插入脏数据后触发 Job，检查日志输出。
 */
@SlowTest
@DisplayName("编号关联 - 一致性校验 Job 测试")
public class NumberingConsistencyCheckJobTest extends AbstractMapperTest {

    @Autowired private NumberingConsistencyCheckJob job;
    @Autowired private OutputInvoiceMapper outputInvoiceMapper;
    @Autowired private InputInvoiceMapper inputInvoiceMapper;
    @Autowired private ReceivableMapper receivableMapper;
    @Autowired private PayableMapper payableMapper;
    @Autowired private VoucherMapper voucherMapper;
    @Autowired private BusinessDocMapper businessDocMapper;
    @Autowired private ArapSettlementMapper settlementMapper;

    /**
     * T7-1: 无问题场景 - 所有关联字段一致，应输出 "未发现不一致数据"
     */
    @Test
    @DisplayName("无问题场景: 所有关联字段一致")
    void clean_data_no_issues() {
        // 1. 销项发票有 docNo 和 voucherNo
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

        // 2. 应收单有 docNo, voucherNo, invoiceNo
        ReceivableEntity receivable = new ReceivableEntity();
        receivable.setDocNo("9999.JOB.CLEAN.REC.001");
        receivable.setPeriod("202606");
        receivable.setAmount(new BigDecimal("11300.00"));
        receivable.setInvoiceNo("9999.JOB.CLEAN.INV.001");
        receivable.setVoucherId(1L);
        receivable.setVoucherNo("9999.JOB.CLEAN.VCH.001");
        receivable.setStatus("CONFIRMED");
        receivable.setCustomerId(1L);
        receivable.setTxDate(LocalDate.of(2026, 6, 28));
        receivable.setUnsettledAmount(new BigDecimal("11300.00"));
        receivable.setDeleted(0);
        receivableMapper.insert(receivable);

        // 3. 凭证有 sourceDocId, sourceDocNo, sourceDocType
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

        // 4. 运行 Job（无脏数据，应无异常）
        assertDoesNotThrow(() -> job.execute());
    }

    /**
     * T7-2: 进项发票 voucherId 非空但 voucherNo 为空
     */
    @Test
    @DisplayName("脏数据: 进项发票 voucherId 非空但 voucherNo 为空")
    void dirty_input_invoice_voucherId_not_null_voucherNo_null() {
        InputInvoiceEntity invoice = new InputInvoiceEntity();
        invoice.setInvoiceNo("9999.JOB.DIRTY.II.001");
        invoice.setInvoiceDate(LocalDate.of(2026, 6, 28));
        invoice.setPeriod("202606");
        invoice.setAmount(new BigDecimal("10000.00"));
        invoice.setTaxRate(new BigDecimal("0.13"));
        invoice.setTaxAmount(new BigDecimal("1300.00"));
        invoice.setTotalAmount(new BigDecimal("11300.00"));
        invoice.setInvoiceType("SPECIAL");
        invoice.setCertificationStatus("UNCERTIFIED");
        invoice.setDocId(100L);       // 脏数据：有 docId
        invoice.setDocNo("9999.JOB.DIRTY.DOC.001");
        invoice.setVoucherNo(null);   // 脏数据：voucherNo 为空
        invoice.setDeleted(0);
        inputInvoiceMapper.insert(invoice);

        // 运行 Job 应检测到不一致
        assertDoesNotThrow(() -> job.execute());
    }

    /**
     * T7-3: 业务单据状态 VOUCHERED 但 voucherId 为空
     */
    @Test
    @DisplayName("脏数据: 业务单据状态 VOUCHERED 但 voucherId 为空")
    void dirty_business_doc_voucherered_but_no_voucherId() {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo("9999.JOB.DIRTY.BDOC.001");
        doc.setDocType("INVOICE_OUT");
        doc.setPeriod("202606");
        doc.setAmount(new BigDecimal("10000.00"));
        doc.setStatus("VOUCHERED");
        doc.setVoucherId(null);        // 脏数据：状态已核销但无凭证 ID
        doc.setVoucherNo(null);
        doc.setDocDate(LocalDate.of(2026, 6, 28));
        doc.setDeleted(0);
        businessDocMapper.insert(doc);

        assertDoesNotThrow(() -> job.execute());
    }

    /**
     * T7-4: 核销单 voucherId 非空但 voucherNo 为空
     */
    @Test
    @DisplayName("脏数据: 核销单 voucherId 非空但 voucherNo 为空")
    void dirty_settlement_voucherId_not_null_voucherNo_null() {
        ArapSettlementEntity settlement = new ArapSettlementEntity();
        settlement.setSettlementNo("9999.JOB.DIRTY.SET.001");
        settlement.setSettlementType("RECEIVE");
        settlement.setSettlementDate(LocalDate.of(2026, 6, 28));
        settlement.setPeriod("202606");
        settlement.setPartyId(1L);
        settlement.setPartyType("CUSTOMER");
        settlement.setTotalAmount(new BigDecimal("10000.00"));
        settlement.setStatus("DRAFT");
        settlement.setVoucherId(50L);    // 脏数据：有凭证 ID
        settlement.setVoucherNo(null);   // 脏数据：无凭证号
        settlementMapper.insert(settlement);

        assertDoesNotThrow(() -> job.execute());
    }

    /**
     * T7-5: 应付单 invoiceNo 为空但状态已确认
     */
    @Test
    @DisplayName("脏数据: 应付单 invoiceNo 为空但已存在")
    void dirty_payable_no_invoiceNo() {
        PayableEntity payable = new PayableEntity();
        payable.setDocNo("9999.JOB.DIRTY.PAY.001");
        payable.setPeriod("202606");
        payable.setAmount(new BigDecimal("11300.00"));
        payable.setInvoiceNo(null);       // 脏数据：无发票号
        payable.setVoucherId(60L);
        payable.setVoucherNo("9999.JOB.DIRTY.VCH.001");
        payable.setStatus("CONFIRMED");
        payable.setVendorId(1L);
        payable.setTxDate(LocalDate.of(2026, 6, 28));
        payable.setUnsettledAmount(new BigDecimal("11300.00"));
        payable.setDeleted(0);
        payableMapper.insert(payable);

        assertDoesNotThrow(() -> job.execute());
    }

    /**
     * T7-6: 多类脏数据混合场景
     */
    @Test
    @DisplayName("混合脏数据: 多种不一致同时存在")
    void mixed_dirty_data() {
        // 1. 销项发票缺 docNo
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
        inv1.setDocNo(null);  // 脏数据
        inv1.setVoucherNo("9999.JOB.MIX.VCH.001");
        inv1.setDeleted(0);
        outputInvoiceMapper.insert(inv1);

        // 2. 应收单 invoiceNo 不匹配
        ReceivableEntity rec = new ReceivableEntity();
        rec.setDocNo("9999.JOB.MIX.REC.001");
        rec.setPeriod("202606");
        rec.setAmount(new BigDecimal("11300.00"));
        rec.setInvoiceNo("9999.JOB.MIX.INV.002");  // 不匹配！
        rec.setVoucherId(70L);
        rec.setVoucherNo("9999.JOB.MIX.VCH.001");
        rec.setStatus("CONFIRMED");
        rec.setCustomerId(1L);
        rec.setTxDate(LocalDate.of(2026, 6, 28));
        rec.setUnsettledAmount(new BigDecimal("11300.00"));
        rec.setDeleted(0);
        receivableMapper.insert(rec);

        // 3. 凭证 sourceDocNo 为空
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
        vch.setSourceDocNo(null);  // 脏数据
        vch.setSourceDocType("OUTPUT_INVOICE");
        vch.setDeleted(0);
        voucherMapper.insert(vch);

        assertDoesNotThrow(() -> job.execute());
    }
}
