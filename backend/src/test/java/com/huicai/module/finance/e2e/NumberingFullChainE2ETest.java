package com.huicai.module.finance.e2e;

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
 * P3 完整 E2E 链路测试 — 端到端编号链路透溯验证
 *
 * 验证从业务单据创建到凭证生成的完整链路中，
 * 编号关联是否正确传递、可追溯、一致。
 *
 * 测试策略：
 * 1. 插入完整链路数据（模拟真实业务流程）
 * 2. 验证每条记录的编号字段正确传递
 * 3. 验证凭证的 sourceDoc 字段指向正确
 * 4. 验证核销单的编号传递
 */
@SlowTest
@DisplayName("P3 完整 E2E 链路测试")
public class NumberingFullChainE2ETest extends AbstractMapperTest {

    @Autowired private BusinessDocMapper businessDocMapper;
    @Autowired private OutputInvoiceMapper outputInvoiceMapper;
    @Autowired private InputInvoiceMapper inputInvoiceMapper;
    @Autowired private ReceivableMapper receivableMapper;
    @Autowired private PayableMapper payableMapper;
    @Autowired private ArapSettlementMapper settlementMapper;
    @Autowired private VoucherMapper voucherMapper;

    // ========================================
    // T3-1: 销售完整链路 — 端到端编号传递验证
    // ========================================

    @Test
    @DisplayName("T3-1: 销售完整链路 — BusinessDoc → OutputInvoice → Receivable → Settlement → Voucher")
    void sales_full_chain() {
        // === Step 1: 创建业务单据 ===
        String salesDocNo = "9999.P3.SALES.DOC.001";
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo(salesDocNo);
        doc.setDocType("INVOICE_OUT");
        doc.setPeriod("202606");
        doc.setAmount(new BigDecimal("10000.00"));
        doc.setStatus("SUBMITTED");
        doc.setDocDate(LocalDate.of(2026, 6, 28));
        doc.setDeleted(0);
        businessDocMapper.insert(doc);

        Long docId = doc.getId();

        // === Step 2: 生成销售发票 ===
        String invoiceNo = "9999.P3.SALES.INV.001";
        OutputInvoiceEntity invoice = new OutputInvoiceEntity();
        invoice.setInvoiceNo(invoiceNo);
        invoice.setInvoiceDate(LocalDate.of(2026, 6, 28));
        invoice.setPeriod("202606");
        invoice.setAmount(new BigDecimal("10000.00"));
        invoice.setTaxRate(new BigDecimal("0.13"));
        invoice.setTaxAmount(new BigDecimal("1300.00"));
        invoice.setTotalAmount(new BigDecimal("11300.00"));
        invoice.setInvoiceType("SPECIAL");
        invoice.setStatus("CONFIRMED");
        invoice.setDocId(docId);
        invoice.setDocNo(salesDocNo);
        invoice.setDeleted(0);
        outputInvoiceMapper.insert(invoice);

        Long invoiceId = invoice.getId();

        // === Step 3: 生成应收单 ===
        String receivableNo = "9999.P3.SALES.REC.001";
        ReceivableEntity receivable = new ReceivableEntity();
        receivable.setDocNo(receivableNo);
        receivable.setPeriod("202606");
        receivable.setAmount(new BigDecimal("11300.00"));
        receivable.setInvoiceNo(invoiceNo);
        receivable.setDocId(docId);
        receivable.setDocNo(salesDocNo);
        receivable.setCustomerId(1L);
        receivable.setTxDate(LocalDate.of(2026, 6, 28));
        receivable.setUnsettledAmount(new BigDecimal("11300.00"));
        receivable.setStatus("CONFIRMED");
        receivable.setDeleted(0);
        receivableMapper.insert(receivable);

        Long receivableId = receivable.getId();

        // === Step 4: 生成核销单 ===
        String settlementNo = "9999.P3.SALES.SET.001";
        ArapSettlementEntity settlement = new ArapSettlementEntity();
        settlement.setSettlementNo(settlementNo);
        settlement.setSettlementType("RECEIVE");
        settlement.setSettlementDate(LocalDate.of(2026, 6, 28));
        settlement.setPeriod("202606");
        settlement.setPartyId(1L);
        settlement.setPartyType("CUSTOMER");
        settlement.setTotalAmount(new BigDecimal("11300.00"));
        settlement.setStatus("DRAFT");
        settlementMapper.insert(settlement);

        Long settlementId = settlement.getId();

        // === Step 5: 生成凭证 ===
        String voucherNo = "9999.P3.SALES.VCH.001";
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNo);
        voucher.setPeriod("202606");
        voucher.setVoucherTypeId(1L);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary("P3 销售链路凭证");
        voucher.setTotalDebit(new BigDecimal("11300.00"));
        voucher.setTotalCredit(new BigDecimal("11300.00"));
        voucher.setSourceDocId(invoiceId);
        voucher.setSourceDocNo(invoiceNo);
        voucher.setSourceDocType("OUTPUT_INVOICE");
        voucher.setDeleted(0);
        voucherMapper.insert(voucher);

        // === Step 6: 回写凭证编号到各实体 ===
        invoice.setVoucherNo(voucherNo);
        invoice.setVoucherId(voucher.getId());
        outputInvoiceMapper.updateById(invoice);

        receivable.setVoucherNo(voucherNo);
        receivable.setVoucherId(voucher.getId());
        receivableMapper.updateById(receivable);

        settlement.setVoucherNo(voucherNo);
        settlement.setVoucherId(voucher.getId());
        settlementMapper.updateById(settlement);

        doc.setVoucherNo(voucherNo);
        doc.setVoucherId(voucher.getId());
        businessDocMapper.updateById(doc);

        // === 验证: 编号链路透溯 ===
        // 1. 业务单据 → 销售发票
        OutputInvoiceEntity loadedInvoice = outputInvoiceMapper.selectById(invoiceId);
        assertNotNull(loadedInvoice);
        assertEquals(salesDocNo, loadedInvoice.getDocNo());
        assertEquals(docId, loadedInvoice.getDocId());
        assertEquals(voucherNo, loadedInvoice.getVoucherNo());
        assertEquals(voucher.getId(), loadedInvoice.getVoucherId());

        // 2. 销售发票 → 应收单
        ReceivableEntity loadedReceivable = receivableMapper.selectById(receivableId);
        assertNotNull(loadedReceivable);
        assertEquals(invoiceNo, loadedReceivable.getInvoiceNo());
        assertEquals(salesDocNo, loadedReceivable.getDocNo());
        assertEquals(docId, loadedReceivable.getDocId());
        assertEquals(voucherNo, loadedReceivable.getVoucherNo());
        assertEquals(voucher.getId(), loadedReceivable.getVoucherId());

        // 3. 应收单 → 核销单
        ArapSettlementEntity loadedSettlement = settlementMapper.selectById(settlementId);
        assertNotNull(loadedSettlement);
        assertEquals(voucherNo, loadedSettlement.getVoucherNo());
        assertEquals(voucher.getId(), loadedSettlement.getVoucherId());

        // 4. 核销单 → 凭证
        VoucherEntity loadedVoucher = voucherMapper.selectById(voucher.getId());
        assertNotNull(loadedVoucher);
        assertEquals(voucherNo, loadedVoucher.getVoucherNo());
        assertEquals(invoiceId, loadedVoucher.getSourceDocId());
        assertEquals(invoiceNo, loadedVoucher.getSourceDocNo());
        assertEquals("OUTPUT_INVOICE", loadedVoucher.getSourceDocType());

        // 5. 凭证 → 业务单据（反向追溯）
        BusinessDocEntity loadedDoc = businessDocMapper.selectById(docId);
        assertNotNull(loadedDoc);
        assertEquals(voucherNo, loadedDoc.getVoucherNo());
        assertEquals(voucher.getId(), loadedDoc.getVoucherId());
    }

    // ========================================
    // T3-2: 采购完整链路 — 端到端编号传递验证
    // ========================================

    @Test
    @DisplayName("T3-2: 采购完整链路 — BusinessDoc → InputInvoice → Payable → Settlement → Voucher")
    void procurement_full_chain() {
        // === Step 1: 创建业务单据 ===
        String purchaseDocNo = "9999.P3.PURCH.DOC.001";
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo(purchaseDocNo);
        doc.setDocType("INVOICE_IN");
        doc.setPeriod("202606");
        doc.setAmount(new BigDecimal("10000.00"));
        doc.setStatus("SUBMITTED");
        doc.setDocDate(LocalDate.of(2026, 6, 28));
        doc.setDeleted(0);
        businessDocMapper.insert(doc);

        Long docId = doc.getId();

        // === Step 2: 生成采购发票 ===
        String invoiceNo = "9999.P3.PURCH.INV.001";
        InputInvoiceEntity invoice = new InputInvoiceEntity();
        invoice.setInvoiceNo(invoiceNo);
        invoice.setInvoiceDate(LocalDate.of(2026, 6, 28));
        invoice.setPeriod("202606");
        invoice.setAmount(new BigDecimal("10000.00"));
        invoice.setTaxRate(new BigDecimal("0.13"));
        invoice.setTaxAmount(new BigDecimal("1300.00"));
        invoice.setTotalAmount(new BigDecimal("11300.00"));
        invoice.setInvoiceType("SPECIAL");
        invoice.setCertificationStatus("UNCERTIFIED");
        invoice.setDocId(docId);
        invoice.setDocNo(purchaseDocNo);
        invoice.setDeleted(0);
        inputInvoiceMapper.insert(invoice);

        Long invoiceId = invoice.getId();

        // === Step 3: 生成应付单 ===
        String payableNo = "9999.P3.PURCH.PAY.001";
        PayableEntity payable = new PayableEntity();
        payable.setDocNo(payableNo);
        payable.setPeriod("202606");
        payable.setAmount(new BigDecimal("11300.00"));
        payable.setInvoiceNo(invoiceNo);
        payable.setDocId(docId);
        payable.setDocNo(purchaseDocNo);
        payable.setVendorId(1L);
        payable.setTxDate(LocalDate.of(2026, 6, 28));
        payable.setUnsettledAmount(new BigDecimal("11300.00"));
        payable.setStatus("CONFIRMED");
        payable.setDeleted(0);
        payableMapper.insert(payable);

        Long payableId = payable.getId();

        // === Step 4: 生成核销单 ===
        String settlementNo = "9999.P3.PURCH.SET.001";
        ArapSettlementEntity settlement = new ArapSettlementEntity();
        settlement.setSettlementNo(settlementNo);
        settlement.setSettlementType("PAY");
        settlement.setSettlementDate(LocalDate.of(2026, 6, 28));
        settlement.setPeriod("202606");
        settlement.setPartyId(1L);
        settlement.setPartyType("VENDOR");
        settlement.setTotalAmount(new BigDecimal("11300.00"));
        settlement.setStatus("DRAFT");
        settlementMapper.insert(settlement);

        Long settlementId = settlement.getId();

        // === Step 5: 生成凭证 ===
        String voucherNo = "9999.P3.PURCH.VCH.001";
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNo);
        voucher.setPeriod("202606");
        voucher.setVoucherTypeId(1L);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary("P3 采购链路凭证");
        voucher.setTotalDebit(new BigDecimal("11300.00"));
        voucher.setTotalCredit(new BigDecimal("11300.00"));
        voucher.setSourceDocId(invoiceId);
        voucher.setSourceDocNo(invoiceNo);
        voucher.setSourceDocType("INPUT_INVOICE");
        voucher.setDeleted(0);
        voucherMapper.insert(voucher);

        // === Step 6: 回写凭证编号到各实体 ===
        invoice.setVoucherNo(voucherNo);
        invoice.setVoucherId(voucher.getId());
        inputInvoiceMapper.updateById(invoice);

        payable.setVoucherNo(voucherNo);
        payable.setVoucherId(voucher.getId());
        payableMapper.updateById(payable);

        settlement.setVoucherNo(voucherNo);
        settlement.setVoucherId(voucher.getId());
        settlementMapper.updateById(settlement);

        doc.setVoucherNo(voucherNo);
        doc.setVoucherId(voucher.getId());
        businessDocMapper.updateById(doc);

        // === 验证: 编号链路透溯 ===
        // 1. 业务单据 → 采购发票
        InputInvoiceEntity loadedInvoice = inputInvoiceMapper.selectById(invoiceId);
        assertNotNull(loadedInvoice);
        assertEquals(purchaseDocNo, loadedInvoice.getDocNo());
        assertEquals(docId, loadedInvoice.getDocId());
        assertEquals(voucherNo, loadedInvoice.getVoucherNo());
        assertEquals(voucher.getId(), loadedInvoice.getVoucherId());

        // 2. 采购发票 → 应付单
        PayableEntity loadedPayable = payableMapper.selectById(payableId);
        assertNotNull(loadedPayable);
        assertEquals(invoiceNo, loadedPayable.getInvoiceNo());
        assertEquals(purchaseDocNo, loadedPayable.getDocNo());
        assertEquals(docId, loadedPayable.getDocId());
        assertEquals(voucherNo, loadedPayable.getVoucherNo());
        assertEquals(voucher.getId(), loadedPayable.getVoucherId());

        // 3. 应付单 → 核销单
        ArapSettlementEntity loadedSettlement = settlementMapper.selectById(settlementId);
        assertNotNull(loadedSettlement);
        assertEquals(voucherNo, loadedSettlement.getVoucherNo());
        assertEquals(voucher.getId(), loadedSettlement.getVoucherId());

        // 4. 核销单 → 凭证
        VoucherEntity loadedVoucher = voucherMapper.selectById(voucher.getId());
        assertNotNull(loadedVoucher);
        assertEquals(voucherNo, loadedVoucher.getVoucherNo());
        assertEquals(invoiceId, loadedVoucher.getSourceDocId());
        assertEquals(invoiceNo, loadedVoucher.getSourceDocNo());
        assertEquals("INPUT_INVOICE", loadedVoucher.getSourceDocType());

        // 5. 凭证 → 业务单据（反向追溯）
        BusinessDocEntity loadedDoc = businessDocMapper.selectById(docId);
        assertNotNull(loadedDoc);
        assertEquals(voucherNo, loadedDoc.getVoucherNo());
        assertEquals(voucher.getId(), loadedDoc.getVoucherId());
    }

    // ========================================
    // T3-3: 混合链路 — 销售+采购共用同一凭证
    // ========================================

    @Test
    @DisplayName("T3-3: 混合链路 — 应收+应付共用同一核销单+凭证")
    void mixed_chain_shared_voucher() {
        // === 创建销售链路 ===
        String salesDocNo = "9999.P3.MIX.SALES.DOC.001";
        BusinessDocEntity salesDoc = new BusinessDocEntity();
        salesDoc.setDocNo(salesDocNo);
        salesDoc.setDocType("INVOICE_OUT");
        salesDoc.setPeriod("202606");
        salesDoc.setAmount(new BigDecimal("10000.00"));
        salesDoc.setStatus("SUBMITTED");
        salesDoc.setDocDate(LocalDate.of(2026, 6, 28));
        salesDoc.setDeleted(0);
        businessDocMapper.insert(salesDoc);

        String salesInvoiceNo = "9999.P3.MIX.SALES.INV.001";
        OutputInvoiceEntity salesInvoice = new OutputInvoiceEntity();
        salesInvoice.setInvoiceNo(salesInvoiceNo);
        salesInvoice.setInvoiceDate(LocalDate.of(2026, 6, 28));
        salesInvoice.setPeriod("202606");
        salesInvoice.setAmount(new BigDecimal("10000.00"));
        salesInvoice.setTaxRate(new BigDecimal("0.13"));
        salesInvoice.setTaxAmount(new BigDecimal("1300.00"));
        salesInvoice.setTotalAmount(new BigDecimal("11300.00"));
        salesInvoice.setInvoiceType("SPECIAL");
        salesInvoice.setStatus("CONFIRMED");
        salesInvoice.setDocId(salesDoc.getId());
        salesInvoice.setDocNo(salesDocNo);
        salesInvoice.setDeleted(0);
        outputInvoiceMapper.insert(salesInvoice);

        String salesRecNo = "9999.P3.MIX.SALES.REC.001";
        ReceivableEntity salesRec = new ReceivableEntity();
        salesRec.setDocNo(salesRecNo);
        salesRec.setPeriod("202606");
        salesRec.setAmount(new BigDecimal("11300.00"));
        salesRec.setInvoiceNo(salesInvoiceNo);
        salesRec.setDocId(salesDoc.getId());
        salesRec.setDocNo(salesDocNo);
        salesRec.setCustomerId(1L);
        salesRec.setTxDate(LocalDate.of(2026, 6, 28));
        salesRec.setUnsettledAmount(new BigDecimal("11300.00"));
        salesRec.setStatus("CONFIRMED");
        salesRec.setDeleted(0);
        receivableMapper.insert(salesRec);

        // === 创建采购链路 ===
        String purchDocNo = "9999.P3.MIX.PURCH.DOC.001";
        BusinessDocEntity purchDoc = new BusinessDocEntity();
        purchDoc.setDocNo(purchDocNo);
        purchDoc.setDocType("INVOICE_IN");
        purchDoc.setPeriod("202606");
        purchDoc.setAmount(new BigDecimal("10000.00"));
        purchDoc.setStatus("SUBMITTED");
        purchDoc.setDocDate(LocalDate.of(2026, 6, 28));
        purchDoc.setDeleted(0);
        businessDocMapper.insert(purchDoc);

        String purchInvoiceNo = "9999.P3.MIX.PURCH.INV.001";
        InputInvoiceEntity purchInvoice = new InputInvoiceEntity();
        purchInvoice.setInvoiceNo(purchInvoiceNo);
        purchInvoice.setInvoiceDate(LocalDate.of(2026, 6, 28));
        purchInvoice.setPeriod("202606");
        purchInvoice.setAmount(new BigDecimal("10000.00"));
        purchInvoice.setTaxRate(new BigDecimal("0.13"));
        purchInvoice.setTaxAmount(new BigDecimal("1300.00"));
        purchInvoice.setTotalAmount(new BigDecimal("11300.00"));
        purchInvoice.setInvoiceType("SPECIAL");
        purchInvoice.setCertificationStatus("UNCERTIFIED");
        purchInvoice.setDocId(purchDoc.getId());
        purchInvoice.setDocNo(purchDocNo);
        purchInvoice.setDeleted(0);
        inputInvoiceMapper.insert(purchInvoice);

        String purchPayNo = "9999.P3.MIX.PURCH.PAY.001";
        PayableEntity purchPay = new PayableEntity();
        purchPay.setDocNo(purchPayNo);
        purchPay.setPeriod("202606");
        purchPay.setAmount(new BigDecimal("11300.00"));
        purchPay.setInvoiceNo(purchInvoiceNo);
        purchPay.setDocId(purchDoc.getId());
        purchPay.setDocNo(purchDocNo);
        purchPay.setVendorId(1L);
        purchPay.setTxDate(LocalDate.of(2026, 6, 28));
        purchPay.setUnsettledAmount(new BigDecimal("11300.00"));
        purchPay.setStatus("CONFIRMED");
        purchPay.setDeleted(0);
        payableMapper.insert(purchPay);

        // === 创建核销单（同时核销应收和应付）===
        String settlementNo = "9999.P3.MIX.SET.001";
        ArapSettlementEntity settlement = new ArapSettlementEntity();
        settlement.setSettlementNo(settlementNo);
        settlement.setSettlementType("RECEIVE");
        settlement.setSettlementDate(LocalDate.of(2026, 6, 28));
        settlement.setPeriod("202606");
        settlement.setPartyId(1L);
        settlement.setPartyType("CUSTOMER");
        settlement.setTotalAmount(new BigDecimal("22600.00"));
        settlement.setStatus("DRAFT");
        settlementMapper.insert(settlement);

        // === 生成凭证（核销单 → 凭证）===
        String voucherNo = "9999.P3.MIX.VCH.001";
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNo);
        voucher.setPeriod("202606");
        voucher.setVoucherTypeId(1L);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary("P3 混合链路凭证");
        voucher.setTotalDebit(new BigDecimal("22600.00"));
        voucher.setTotalCredit(new BigDecimal("22600.00"));
        voucher.setSourceDocId(settlement.getId());
        voucher.setSourceDocNo(settlementNo);
        voucher.setSourceDocType("SETTLEMENT");
        voucher.setDeleted(0);
        voucherMapper.insert(voucher);

        // === 回写凭证编号到所有实体 ===
        salesInvoice.setVoucherNo(voucherNo);
        salesInvoice.setVoucherId(voucher.getId());
        outputInvoiceMapper.updateById(salesInvoice);

        salesRec.setVoucherNo(voucherNo);
        salesRec.setVoucherId(voucher.getId());
        receivableMapper.updateById(salesRec);

        purchInvoice.setVoucherNo(voucherNo);
        purchInvoice.setVoucherId(voucher.getId());
        inputInvoiceMapper.updateById(purchInvoice);

        purchPay.setVoucherNo(voucherNo);
        purchPay.setVoucherId(voucher.getId());
        payableMapper.updateById(purchPay);

        settlement.setVoucherNo(voucherNo);
        settlement.setVoucherId(voucher.getId());
        settlementMapper.updateById(settlement);

        salesDoc.setVoucherNo(voucherNo);
        salesDoc.setVoucherId(voucher.getId());
        businessDocMapper.updateById(salesDoc);

        purchDoc.setVoucherNo(voucherNo);
        purchDoc.setVoucherId(voucher.getId());
        businessDocMapper.updateById(purchDoc);

        // === 验证: 所有实体都有相同的 voucherNo ===
        OutputInvoiceEntity loadedSalesInv = outputInvoiceMapper.selectById(salesInvoice.getId());
        InputInvoiceEntity loadedPurchInv = inputInvoiceMapper.selectById(purchInvoice.getId());
        ReceivableEntity loadedSalesRec = receivableMapper.selectById(salesRec.getId());
        PayableEntity loadedPurchPay = payableMapper.selectById(purchPay.getId());
        ArapSettlementEntity loadedSettlement = settlementMapper.selectById(settlement.getId());
        VoucherEntity loadedVoucher = voucherMapper.selectById(voucher.getId());
        BusinessDocEntity loadedSalesDoc = businessDocMapper.selectById(salesDoc.getId());
        BusinessDocEntity loadedPurchDoc = businessDocMapper.selectById(purchDoc.getId());

        assertEquals(voucherNo, loadedSalesInv.getVoucherNo());
        assertEquals(voucherNo, loadedPurchInv.getVoucherNo());
        assertEquals(voucherNo, loadedSalesRec.getVoucherNo());
        assertEquals(voucherNo, loadedPurchPay.getVoucherNo());
        assertEquals(voucherNo, loadedSettlement.getVoucherNo());
        assertEquals(voucherNo, loadedVoucher.getVoucherNo());
        assertEquals(voucherNo, loadedSalesDoc.getVoucherNo());
        assertEquals(voucherNo, loadedPurchDoc.getVoucherNo());

        // === 验证: 凭证溯源指向核销单 ===
        assertEquals(settlement.getId(), loadedVoucher.getSourceDocId());
        assertEquals(settlementNo, loadedVoucher.getSourceDocNo());
        assertEquals("SETTLEMENT", loadedVoucher.getSourceDocType());
    }
}
