package com.huicai.base.voucher.e2e;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.common.test.SlowTest;
import com.huicai.base.business.entity.ArapSettlementEntity;
import com.huicai.base.business.mapper.ArapSettlementMapper;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
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

/**
 * P3 完整 E2E 链路测试：业务单 → 发票 → 核销单 → 凭证。
 *
 * <h3>历史说明（REQ-2026-119 / 慢测 C 类）</h3>
 * 本测试存在 4 类与当前 schema 不符的写法：
 * <ol>
 *   <li><b>单据状态误用发票状态</b>：{@code doc.setStatus("CONFIRMED")}，而
 *       {@code t_business_doc.chk_doc_status} 允许集为
 *       DRAFT/SUBMITTED/APPROVED/VOUCHERED/PARTIALLY_RECONCILED/FULLY_RECONCILED/
 *       CLOSED/REJECTED/REVERSED —— {@code CONFIRMED} 属发票侧状态。
 *       （注意 {@code t_arap_settlement} 的 {@code chk_settlement_status} <b>确实</b>含
 *       CONFIRMED，两者允许集不同，不可混用。）</li>
 *   <li><b>断言不存在的编号冗余列</b>：{@code invoice.docNo} /
 *       {@code invoice.voucherNo} / {@code settlement.voucherNo} 均标注
 *       {@code @TableField(exist = false)}（注释明写「DB 无此列」），经 DB 往返必为
 *       null，原断言无法成立；</li>
 *   <li><b>凭证溯源用已废弃字段</b>：{@code voucher.sourceDocId} /
 *       {@code sourceDocNo} / {@code sourceDocType} 亦为 {@code exist=false}，
 *       凭证→单据的真实关联列是 {@code t_voucher.business_doc_id}
 *       （带 FK {@code fk_voucher_business_doc}）；</li>
 *   <li>{@code invoice.setDocNo(...)} 等对幽灵字段的赋值对 INSERT/UPDATE 无效，
 *       属误导性代码。</li>
 * </ol>
 * 修法：单据状态改用 {@code APPROVED}（已审核待结算）与 {@code VOUCHERED}（已制证）；
 * 断言全部改用真实持久化列（{@code doc_id} / {@code voucher_id} /
 * {@code business_doc_id} / {@code source_doc_id}），保持「全链路双向可溯源」
 * 这一测试意图不变。
 */
@SlowTest
@DisplayName("P3 完整 E2E 链路测试")
public class NumberingFullChainE2ETest extends AbstractMapperTest {

    private static final String PERIOD = "202606";
    private static final LocalDate DOC_DATE = LocalDate.of(2026, 6, 28);
    private static final BigDecimal NET = new BigDecimal("10000.00");
    private static final BigDecimal TAX = new BigDecimal("1300.00");
    private static final BigDecimal GROSS = new BigDecimal("11300.00");

    /** t_business_doc.chk_doc_status 合法值（发票状态 CONFIRMED 在此非法） */
    private static final String DOC_STATUS_APPROVED = "APPROVED";
    private static final String DOC_STATUS_VOUCHERED = "VOUCHERED";

    @Autowired private BusinessDocMapper businessDocMapper;
    @Autowired private OutputInvoiceMapper outputInvoiceMapper;
    @Autowired private InputInvoiceMapper inputInvoiceMapper;
    @Autowired private ArapSettlementMapper settlementMapper;
    @Autowired private VoucherMapper voucherMapper;

    // ==================== 造数工具 ====================

    private BusinessDocEntity newDoc(String docNo, String docType, Long customerId, Long supplierId) {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo(docNo);
        doc.setDocType(docType);
        doc.setPeriod(PERIOD);
        doc.setDocDate(DOC_DATE);
        doc.setAmount(NET);
        doc.setStatus(DOC_STATUS_APPROVED);
        doc.setCustomerId(customerId);
        doc.setSupplierId(supplierId);
        return doc;
    }

    private ArapSettlementEntity newSettlement(String settlementNo, String type,
                                               String partyType, BigDecimal total) {
        ArapSettlementEntity settlement = new ArapSettlementEntity();
        settlement.setSettlementNo(settlementNo);
        settlement.setSettlementType(type); // RECEIVE / PAY
        settlement.setSettlementDate(DOC_DATE);
        settlement.setPeriod(PERIOD);
        settlement.setPartyId(1L);
        settlement.setPartyType(partyType); // CUSTOMER / VENDOR
        settlement.setTotalAmount(total);
        settlement.setStatus("DRAFT"); // chk_settlement_status 合法值
        return settlement;
    }

    private VoucherEntity newVoucher(String voucherNo, String summary, BigDecimal total,
                                     Long businessDocId) {
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNo);
        voucher.setPeriod(PERIOD);
        voucher.setVoucherTypeId(1L);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary(summary);
        voucher.setTotalDebit(total);
        voucher.setTotalCredit(total);
        // 凭证 → 业务单据的真实溯源列（带 FK），取代已废弃的 source_doc_*
        voucher.setBusinessDocId(businessDocId);
        voucher.setDeleted(0);
        return voucher;
    }

    /** 把凭证回写到发票、���据、核销单三侧（只用真实列） */
    private void linkVoucher(String voucherNo, VoucherEntity voucher, BusinessDocEntity doc,
                             ArapSettlementEntity settlement) {
        doc.setVoucherId(voucher.getId());
        doc.setVoucherNo(voucherNo);
        doc.setStatus(DOC_STATUS_VOUCHERED);
        businessDocMapper.updateById(doc);

        settlement.setVoucherId(voucher.getId());
        // 核销单侧真实溯源列：source_doc_type / source_doc_id（V140）
        settlement.setSourceDocType("VOUCHER");
        settlement.setSourceDocId(voucher.getId());
        settlementMapper.updateById(settlement);
    }

    // ==================== T3-1 销售链路 ====================

    @Test
    @DisplayName("T3-1: 销售完整链路 — BusinessDoc → OutputInvoice → Settlement → Voucher")
    void sales_full_chain() {
        Long customerId = ensureCustomer(DEFAULT_ENTERPRISE_ID);
        String salesDocNo = "9999.P3.SALES.DOC.001";
        String invoiceNo = "9999.P3.SALES.INV.001";
        String settlementNo = "9999.P3.SALES.SET.001";
        String voucherNo = "9999.P3.SALES.VCH.001";

        // 1. 业务单 → 2. 销项发票
        BusinessDocEntity doc = newDoc(salesDocNo, "INVOICE_OUT", customerId, null);
        businessDocMapper.insert(doc);

        OutputInvoiceEntity invoice = new OutputInvoiceEntity();
        invoice.setInvoiceNo(invoiceNo);
        invoice.setInvoiceDate(DOC_DATE);
        invoice.setPeriod(PERIOD);
        invoice.setCustomerId(customerId);
        invoice.setAmount(NET);
        invoice.setTaxRate(new BigDecimal("0.13"));
        invoice.setTaxAmount(TAX);
        invoice.setTotalAmount(GROSS);
        invoice.setInvoiceType("SPECIAL");
        invoice.setStatus("CONFIRMED"); // 发票侧状态，允许
        invoice.setDocId(doc.getId());
        invoice.setDeleted(0);
        outputInvoiceMapper.insert(invoice);

        // 发票 ↔ 单据 双向对齐
        doc.setInvoiceId(invoice.getId());
        doc.setInvoiceNo(invoiceNo);
        doc.setUnsettledAmount(GROSS);
        businessDocMapper.updateById(doc);

        // 3. 核销单
        ArapSettlementEntity settlement = newSettlement(settlementNo, "RECEIVE", "CUSTOMER", GROSS);
        settlementMapper.insert(settlement);

        // 4. 凭证
        VoucherEntity voucher = newVoucher(voucherNo, "P3 销售链路凭证", GROSS, doc.getId());
        voucherMapper.insert(voucher);

        invoice.setVoucherId(voucher.getId());
        outputInvoiceMapper.updateById(invoice);

        linkVoucher(voucherNo, voucher, doc, settlement);

        // ---- 验证全链路双向溯源（只断言真实列）----
        OutputInvoiceEntity loadedInvoice = outputInvoiceMapper.selectById(invoice.getId());
        assertNotNull(loadedInvoice);
        assertEquals(doc.getId(), loadedInvoice.getDocId());
        assertEquals(voucher.getId(), loadedInvoice.getVoucherId());

        BusinessDocEntity loadedDoc = businessDocMapper.selectById(doc.getId());
        assertNotNull(loadedDoc);
        assertEquals(invoiceNo, loadedDoc.getInvoiceNo());
        assertEquals(invoice.getId(), loadedDoc.getInvoiceId());
        assertEquals(voucherNo, loadedDoc.getVoucherNo());
        assertEquals(voucher.getId(), loadedDoc.getVoucherId());
        assertEquals(DOC_STATUS_VOUCHERED, loadedDoc.getStatus());

        ArapSettlementEntity loadedSettlement = settlementMapper.selectById(settlement.getId());
        assertNotNull(loadedSettlement);
        assertEquals(voucher.getId(), loadedSettlement.getVoucherId());
        assertEquals("VOUCHER", loadedSettlement.getSourceDocType());
        assertEquals(voucher.getId(), loadedSettlement.getSourceDocId());

        VoucherEntity loadedVoucher = voucherMapper.selectById(voucher.getId());
        assertNotNull(loadedVoucher);
        assertEquals(voucherNo, loadedVoucher.getVoucherNo());
        assertEquals(doc.getId(), loadedVoucher.getBusinessDocId());
        // 负向：已废弃的 source_doc_* 不落库
        assertNull(loadedVoucher.getSourceDocNo(), "t_voucher 无 source_doc_no 列");
    }

    // ==================== T3-2 采购链路 ====================

    @Test
    @DisplayName("T3-2: 采购完整链路 — BusinessDoc → InputInvoice → Settlement → Voucher")
    void procurement_full_chain() {
        Long vendorId = ensureVendor(DEFAULT_ENTERPRISE_ID);
        String purchDocNo = "9999.P3.PURCH.DOC.001";
        String invoiceNo = "9999.P3.PURCH.INV.001";
        String settlementNo = "9999.P3.PURCH.SET.001";
        String voucherNo = "9999.P3.PURCH.VCH.001";

        BusinessDocEntity doc = newDoc(purchDocNo, "INVOICE_IN", null, vendorId);
        businessDocMapper.insert(doc);

        InputInvoiceEntity invoice = new InputInvoiceEntity();
        invoice.setInvoiceNo(invoiceNo);
        invoice.setInvoiceDate(DOC_DATE);
        invoice.setPeriod(PERIOD);
        invoice.setVendorId(vendorId);
        invoice.setAmount(NET);
        invoice.setTaxRate(new BigDecimal("0.13"));
        invoice.setTaxAmount(TAX);
        invoice.setTotalAmount(GROSS);
        invoice.setInvoiceType("SPECIAL");
        invoice.setCertificationStatus("UNCERTIFIED");
        invoice.setStatus("CONFIRMED");
        invoice.setDocId(doc.getId());
        invoice.setDeleted(0);
        inputInvoiceMapper.insert(invoice);

        doc.setInvoiceId(invoice.getId());
        doc.setInvoiceNo(invoiceNo);
        doc.setUnsettledAmount(GROSS);
        businessDocMapper.updateById(doc);

        ArapSettlementEntity settlement = newSettlement(settlementNo, "PAY", "VENDOR", GROSS);
        settlementMapper.insert(settlement);

        VoucherEntity voucher = newVoucher(voucherNo, "P3 采购链路凭证", GROSS, doc.getId());
        voucherMapper.insert(voucher);

        invoice.setVoucherId(voucher.getId());
        inputInvoiceMapper.updateById(invoice);

        linkVoucher(voucherNo, voucher, doc, settlement);

        InputInvoiceEntity loadedInvoice = inputInvoiceMapper.selectById(invoice.getId());
        assertNotNull(loadedInvoice);
        assertEquals(doc.getId(), loadedInvoice.getDocId());
        assertEquals(voucher.getId(), loadedInvoice.getVoucherId());

        BusinessDocEntity loadedDoc = businessDocMapper.selectById(doc.getId());
        assertNotNull(loadedDoc);
        assertEquals(invoiceNo, loadedDoc.getInvoiceNo());
        assertEquals(voucherNo, loadedDoc.getVoucherNo());
        assertEquals(voucher.getId(), loadedDoc.getVoucherId());

        ArapSettlementEntity loadedSettlement = settlementMapper.selectById(settlement.getId());
        assertNotNull(loadedSettlement);
        assertEquals(voucher.getId(), loadedSettlement.getVoucherId());
        assertEquals(voucher.getId(), loadedSettlement.getSourceDocId());

        VoucherEntity loadedVoucher = voucherMapper.selectById(voucher.getId());
        assertNotNull(loadedVoucher);
        assertEquals(voucherNo, loadedVoucher.getVoucherNo());
        assertEquals(doc.getId(), loadedVoucher.getBusinessDocId());
        assertNull(loadedVoucher.getSourceDocType(), "t_voucher 无 source_doc_type 列");
    }

    // ==================== T3-3 混合链路 ====================

    @Test
    @DisplayName("T3-3: 混合链路 — 应收+应付共用同一核销单+凭证")
    void mixed_chain_shared_voucher() {
        Long customerId = ensureCustomer(DEFAULT_ENTERPRISE_ID);
        Long vendorId = ensureVendor(DEFAULT_ENTERPRISE_ID);
        String salesDocNo = "9999.P3.MIX.SALES.DOC.001";
        String purchDocNo = "9999.P3.MIX.PURCH.DOC.001";
        String salesInvNo = "9999.P3.MIX.SALES.INV.001";
        String purchInvNo = "9999.P3.MIX.PURCH.INV.001";
        String settlementNo = "9999.P3.MIX.SET.001";
        String voucherNo = "9999.P3.MIX.VCH.001";
        BigDecimal mixedTotal = GROSS.multiply(new BigDecimal("2"));

        // 应收侧
        BusinessDocEntity salesDoc = newDoc(salesDocNo, "INVOICE_OUT", customerId, null);
        businessDocMapper.insert(salesDoc);
        OutputInvoiceEntity salesInvoice = new OutputInvoiceEntity();
        salesInvoice.setInvoiceNo(salesInvNo);
        salesInvoice.setInvoiceDate(DOC_DATE);
        salesInvoice.setPeriod(PERIOD);
        salesInvoice.setCustomerId(customerId);
        salesInvoice.setAmount(NET);
        salesInvoice.setTaxRate(new BigDecimal("0.13"));
        salesInvoice.setTaxAmount(TAX);
        salesInvoice.setTotalAmount(GROSS);
        salesInvoice.setInvoiceType("SPECIAL");
        salesInvoice.setStatus("CONFIRMED");
        salesInvoice.setDocId(salesDoc.getId());
        salesInvoice.setDeleted(0);
        outputInvoiceMapper.insert(salesInvoice);
        salesDoc.setInvoiceId(salesInvoice.getId());
        salesDoc.setInvoiceNo(salesInvNo);
        salesDoc.setUnsettledAmount(GROSS);
        businessDocMapper.updateById(salesDoc);

        // 应付侧
        BusinessDocEntity purchDoc = newDoc(purchDocNo, "INVOICE_IN", null, vendorId);
        businessDocMapper.insert(purchDoc);
        InputInvoiceEntity purchInvoice = new InputInvoiceEntity();
        purchInvoice.setInvoiceNo(purchInvNo);
        purchInvoice.setInvoiceDate(DOC_DATE);
        purchInvoice.setPeriod(PERIOD);
        purchInvoice.setVendorId(vendorId);
        purchInvoice.setAmount(NET);
        purchInvoice.setTaxRate(new BigDecimal("0.13"));
        purchInvoice.setTaxAmount(TAX);
        purchInvoice.setTotalAmount(GROSS);
        purchInvoice.setInvoiceType("SPECIAL");
        purchInvoice.setCertificationStatus("UNCERTIFIED");
        purchInvoice.setStatus("CONFIRMED");
        purchInvoice.setDocId(purchDoc.getId());
        purchInvoice.setDeleted(0);
        inputInvoiceMapper.insert(purchInvoice);
        purchDoc.setInvoiceId(purchInvoice.getId());
        purchDoc.setInvoiceNo(purchInvNo);
        purchDoc.setUnsettledAmount(GROSS);
        businessDocMapper.updateById(purchDoc);

        // 核销单（一单覆盖应收+应付）
        ArapSettlementEntity settlement = newSettlement(settlementNo, "RECEIVE", "CUSTOMER", mixedTotal);
        settlementMapper.insert(settlement);

        // 共用凭证：business_doc_id 指向应收侧单据
        VoucherEntity voucher = newVoucher(voucherNo, "P3 混合链路凭证", mixedTotal, salesDoc.getId());
        voucherMapper.insert(voucher);

        salesInvoice.setVoucherId(voucher.getId());
        outputInvoiceMapper.updateById(salesInvoice);
        purchInvoice.setVoucherId(voucher.getId());
        inputInvoiceMapper.updateById(purchInvoice);

        settlement.setVoucherId(voucher.getId());
        settlement.setSourceDocType("VOUCHER");
        settlement.setSourceDocId(voucher.getId());
        settlementMapper.updateById(settlement);

        salesDoc.setVoucherId(voucher.getId());
        salesDoc.setVoucherNo(voucherNo);
        salesDoc.setStatus(DOC_STATUS_VOUCHERED);
        businessDocMapper.updateById(salesDoc);
        purchDoc.setVoucherId(voucher.getId());
        purchDoc.setVoucherNo(voucherNo);
        purchDoc.setStatus(DOC_STATUS_VOUCHERED);
        businessDocMapper.updateById(purchDoc);

        // ---- 验证：同一凭证被两侧单据与核销单共享 ----
        assertEquals(voucher.getId(), outputInvoiceMapper.selectById(salesInvoice.getId()).getVoucherId());
        assertEquals(voucher.getId(), inputInvoiceMapper.selectById(purchInvoice.getId()).getVoucherId());

        BusinessDocEntity loadedSalesDoc = businessDocMapper.selectById(salesDoc.getId());
        BusinessDocEntity loadedPurchDoc = businessDocMapper.selectById(purchDoc.getId());
        assertEquals(voucherNo, loadedSalesDoc.getVoucherNo());
        assertEquals(voucherNo, loadedPurchDoc.getVoucherNo());
        assertEquals(voucher.getId(), loadedSalesDoc.getVoucherId());
        assertEquals(voucher.getId(), loadedPurchDoc.getVoucherId());

        ArapSettlementEntity loadedSettlement = settlementMapper.selectById(settlement.getId());
        assertEquals(voucher.getId(), loadedSettlement.getVoucherId());
        assertEquals(voucher.getId(), loadedSettlement.getSourceDocId());
        assertEquals("VOUCHER", loadedSettlement.getSourceDocType());
        assertNull(loadedSettlement.getVoucherNo(), "t_arap_settlement 无 voucher_no 列");

        VoucherEntity loadedVoucher = voucherMapper.selectById(voucher.getId());
        assertEquals(voucherNo, loadedVoucher.getVoucherNo());
        assertEquals(salesDoc.getId(), loadedVoucher.getBusinessDocId());
        assertEquals(0, loadedVoucher.getTotalDebit().compareTo(loadedVoucher.getTotalCredit()));
    }
}
