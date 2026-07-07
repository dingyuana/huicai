package com.huicai.module.finance.e2e;

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
@DisplayName("P3 完整 E2E 链路测试")
public class NumberingFullChainE2ETest extends AbstractMapperTest {

    @Autowired private BusinessDocMapper businessDocMapper;
    @Autowired private OutputInvoiceMapper outputInvoiceMapper;
    @Autowired private InputInvoiceMapper inputInvoiceMapper;
    @Autowired private ArapSettlementMapper settlementMapper;
    @Autowired private VoucherMapper voucherMapper;

    @Test
    @DisplayName("T3-1: 销售完整链路 — BusinessDoc → OutputInvoice → Settlement → Voucher")
    void sales_full_chain() {
        String salesDocNo = "9999.P3.SALES.DOC.001";
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo(salesDocNo);
        doc.setDocType("INVOICE_OUT");
        doc.setPeriod("202606");
        doc.setAmount(new BigDecimal("10000.00"));
        doc.setStatus("SUBMITTED");
        doc.setDocDate(LocalDate.of(2026, 6, 28));
        businessDocMapper.insert(doc);

        Long docId = doc.getId();

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

        doc.setInvoiceNo(invoiceNo);
        doc.setCustomerId(1L);
        doc.setUnsettledAmount(new BigDecimal("11300.00"));
        doc.setStatus("CONFIRMED");
        businessDocMapper.updateById(doc);

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

        invoice.setVoucherNo(voucherNo);
        invoice.setVoucherId(voucher.getId());
        outputInvoiceMapper.updateById(invoice);

        settlement.setVoucherNo(voucherNo);
        settlement.setVoucherId(voucher.getId());
        settlementMapper.updateById(settlement);

        doc.setVoucherNo(voucherNo);
        doc.setVoucherId(voucher.getId());
        businessDocMapper.updateById(doc);

        OutputInvoiceEntity loadedInvoice = outputInvoiceMapper.selectById(invoiceId);
        assertNotNull(loadedInvoice);
        assertEquals(salesDocNo, loadedInvoice.getDocNo());
        assertEquals(docId, loadedInvoice.getDocId());
        assertEquals(voucherNo, loadedInvoice.getVoucherNo());
        assertEquals(voucher.getId(), loadedInvoice.getVoucherId());

        BusinessDocEntity loadedDoc = businessDocMapper.selectById(docId);
        assertNotNull(loadedDoc);
        assertEquals(invoiceNo, loadedDoc.getInvoiceNo());
        assertEquals(voucherNo, loadedDoc.getVoucherNo());
        assertEquals(voucher.getId(), loadedDoc.getVoucherId());

        ArapSettlementEntity loadedSettlement = settlementMapper.selectById(settlementId);
        assertNotNull(loadedSettlement);
        assertEquals(voucherNo, loadedSettlement.getVoucherNo());
        assertEquals(voucher.getId(), loadedSettlement.getVoucherId());

        VoucherEntity loadedVoucher = voucherMapper.selectById(voucher.getId());
        assertNotNull(loadedVoucher);
        assertEquals(voucherNo, loadedVoucher.getVoucherNo());
        assertEquals(invoiceId, loadedVoucher.getSourceDocId());
        assertEquals(invoiceNo, loadedVoucher.getSourceDocNo());
        assertEquals("OUTPUT_INVOICE", loadedVoucher.getSourceDocType());
    }

    @Test
    @DisplayName("T3-2: 采购完整链路 — BusinessDoc → InputInvoice → Settlement → Voucher")
    void procurement_full_chain() {
        String purchaseDocNo = "9999.P3.PURCH.DOC.001";
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo(purchaseDocNo);
        doc.setDocType("INVOICE_IN");
        doc.setPeriod("202606");
        doc.setAmount(new BigDecimal("10000.00"));
        doc.setStatus("SUBMITTED");
        doc.setDocDate(LocalDate.of(2026, 6, 28));
        businessDocMapper.insert(doc);

        Long docId = doc.getId();

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

        doc.setInvoiceNo(invoiceNo);
        doc.setSupplierId(1L);
        doc.setUnsettledAmount(new BigDecimal("11300.00"));
        doc.setStatus("CONFIRMED");
        businessDocMapper.updateById(doc);

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

        invoice.setVoucherNo(voucherNo);
        invoice.setVoucherId(voucher.getId());
        inputInvoiceMapper.updateById(invoice);

        settlement.setVoucherNo(voucherNo);
        settlement.setVoucherId(voucher.getId());
        settlementMapper.updateById(settlement);

        doc.setVoucherNo(voucherNo);
        doc.setVoucherId(voucher.getId());
        businessDocMapper.updateById(doc);

        InputInvoiceEntity loadedInvoice = inputInvoiceMapper.selectById(invoiceId);
        assertNotNull(loadedInvoice);
        assertEquals(purchaseDocNo, loadedInvoice.getDocNo());
        assertEquals(docId, loadedInvoice.getDocId());
        assertEquals(voucherNo, loadedInvoice.getVoucherNo());
        assertEquals(voucher.getId(), loadedInvoice.getVoucherId());

        BusinessDocEntity loadedDoc = businessDocMapper.selectById(docId);
        assertNotNull(loadedDoc);
        assertEquals(invoiceNo, loadedDoc.getInvoiceNo());
        assertEquals(voucherNo, loadedDoc.getVoucherNo());
        assertEquals(voucher.getId(), loadedDoc.getVoucherId());

        ArapSettlementEntity loadedSettlement = settlementMapper.selectById(settlementId);
        assertNotNull(loadedSettlement);
        assertEquals(voucherNo, loadedSettlement.getVoucherNo());
        assertEquals(voucher.getId(), loadedSettlement.getVoucherId());

        VoucherEntity loadedVoucher = voucherMapper.selectById(voucher.getId());
        assertNotNull(loadedVoucher);
        assertEquals(voucherNo, loadedVoucher.getVoucherNo());
        assertEquals(invoiceId, loadedVoucher.getSourceDocId());
        assertEquals(invoiceNo, loadedVoucher.getSourceDocNo());
        assertEquals("INPUT_INVOICE", loadedVoucher.getSourceDocType());
    }

    @Test
    @DisplayName("T3-3: 混合链路 — 应收+应付共用同一核销单+凭证")
    void mixed_chain_shared_voucher() {
        String salesDocNo = "9999.P3.MIX.SALES.DOC.001";
        BusinessDocEntity salesDoc = new BusinessDocEntity();
        salesDoc.setDocNo(salesDocNo);
        salesDoc.setDocType("INVOICE_OUT");
        salesDoc.setPeriod("202606");
        salesDoc.setAmount(new BigDecimal("10000.00"));
        salesDoc.setStatus("SUBMITTED");
        salesDoc.setDocDate(LocalDate.of(2026, 6, 28));
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

        salesDoc.setInvoiceNo(salesInvoiceNo);
        salesDoc.setCustomerId(1L);
        salesDoc.setUnsettledAmount(new BigDecimal("11300.00"));
        salesDoc.setStatus("CONFIRMED");
        businessDocMapper.updateById(salesDoc);

        String purchDocNo = "9999.P3.MIX.PURCH.DOC.001";
        BusinessDocEntity purchDoc = new BusinessDocEntity();
        purchDoc.setDocNo(purchDocNo);
        purchDoc.setDocType("INVOICE_IN");
        purchDoc.setPeriod("202606");
        purchDoc.setAmount(new BigDecimal("10000.00"));
        purchDoc.setStatus("SUBMITTED");
        purchDoc.setDocDate(LocalDate.of(2026, 6, 28));
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

        purchDoc.setInvoiceNo(purchInvoiceNo);
        purchDoc.setSupplierId(1L);
        purchDoc.setUnsettledAmount(new BigDecimal("11300.00"));
        purchDoc.setStatus("CONFIRMED");
        businessDocMapper.updateById(purchDoc);

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

        salesInvoice.setVoucherNo(voucherNo);
        salesInvoice.setVoucherId(voucher.getId());
        outputInvoiceMapper.updateById(salesInvoice);

        purchInvoice.setVoucherNo(voucherNo);
        purchInvoice.setVoucherId(voucher.getId());
        inputInvoiceMapper.updateById(purchInvoice);

        settlement.setVoucherNo(voucherNo);
        settlement.setVoucherId(voucher.getId());
        settlementMapper.updateById(settlement);

        salesDoc.setVoucherNo(voucherNo);
        salesDoc.setVoucherId(voucher.getId());
        businessDocMapper.updateById(salesDoc);

        purchDoc.setVoucherNo(voucherNo);
        purchDoc.setVoucherId(voucher.getId());
        businessDocMapper.updateById(purchDoc);

        OutputInvoiceEntity loadedSalesInv = outputInvoiceMapper.selectById(salesInvoice.getId());
        InputInvoiceEntity loadedPurchInv = inputInvoiceMapper.selectById(purchInvoice.getId());
        ArapSettlementEntity loadedSettlement = settlementMapper.selectById(settlement.getId());
        VoucherEntity loadedVoucher = voucherMapper.selectById(voucher.getId());
        BusinessDocEntity loadedSalesDoc = businessDocMapper.selectById(salesDoc.getId());
        BusinessDocEntity loadedPurchDoc = businessDocMapper.selectById(purchDoc.getId());

        assertEquals(voucherNo, loadedSalesInv.getVoucherNo());
        assertEquals(voucherNo, loadedPurchInv.getVoucherNo());
        assertEquals(voucherNo, loadedSettlement.getVoucherNo());
        assertEquals(voucherNo, loadedVoucher.getVoucherNo());
        assertEquals(voucherNo, loadedSalesDoc.getVoucherNo());
        assertEquals(voucherNo, loadedPurchDoc.getVoucherNo());

        assertEquals(settlement.getId(), loadedVoucher.getSourceDocId());
        assertEquals(settlementNo, loadedVoucher.getSourceDocNo());
        assertEquals("SETTLEMENT", loadedVoucher.getSourceDocType());
    }
}