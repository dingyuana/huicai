package com.huicai.base.voucher.e2e;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.base.business.entity.ArapSettlementEntity;
import com.huicai.base.business.entity.ArapSettlementEntryEntity;
import com.huicai.base.business.mapper.ArapSettlementEntryMapper;
import com.huicai.base.business.mapper.ArapSettlementMapper;
import com.huicai.base.business.entity.BusinessDocEntity;
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

/**
 * 编号关联 - 核销链路端到端测试.
 *
 * <h3>历史说明（REQ-2026-121）</h3>
 * 两处业务单据夹具均有两个缺陷：
 * <ol>
 *   <li>{@code t_business_doc.doc_date} 是 NOT NULL，原夹具未赋值；</li>
 *   <li>{@code doc.setStatus("CONFIRMED")} —— 而 {@code t_business_doc.chk_doc_status}
 *       允许集<b>不含</b> CONFIRMED（那是发票侧状态；{@code t_arap_settlement} 才含）。
 *       已改为 APPROVED。</li>
 * </ol>
 */
@DisplayName("编号关联 - 核销链路端到端测试")
public class NumberingSettlementE2ETest extends AbstractMapperTest {

    @Autowired private OutputInvoiceMapper outputInvoiceMapper;
    @Autowired private InputInvoiceMapper inputInvoiceMapper;
    @Autowired private BusinessDocMapper businessDocMapper;
    @Autowired private ArapSettlementMapper settlementMapper;
    @Autowired private ArapSettlementEntryMapper entryMapper;

    @Test
    @DisplayName("核销单与业务单据关联: 应收核销场景")
    void settlement_with_receivable_businessDoc() {
        String invoiceNo = "9999.E2E.SETTLE.INV.001";
        String docNo = "9999.E2E.SETTLE.DOC.001";
        String settlementNo = "9999.E2E.SETTLE.SET.001";

        OutputInvoiceEntity invoice = new OutputInvoiceEntity();
        invoice.setInvoiceNo(invoiceNo);
        invoice.setInvoiceDate(LocalDate.of(2026, 6, 28));
        invoice.setPeriod("202606");
        invoice.setAmount(new BigDecimal("10000.00"));
        invoice.setTaxAmount(new BigDecimal("1300.00"));
        invoice.setTotalAmount(new BigDecimal("11300.00"));
        invoice.setInvoiceType("SPECIAL");
        invoice.setTaxRate(new BigDecimal("0.13"));
        invoice.setStatus("CONFIRMED");
        invoice.setDeleted(0);
        outputInvoiceMapper.insert(invoice);

        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo(docNo);
        doc.setDocType("INVOICE_OUT");
        doc.setPeriod("202606");
        doc.setDocDate(LocalDate.of(2026, 6, 28));
        doc.setAmount(new BigDecimal("11300.00"));
        doc.setInvoiceNo(invoiceNo);
        doc.setStatus("APPROVED");
        doc.setCustomerId(1L);
        doc.setUnsettledAmount(new BigDecimal("11300.00"));
        businessDocMapper.insert(doc);

        ArapSettlementEntity settlement = new ArapSettlementEntity();
        settlement.setSettlementNo(settlementNo);
        // chk_settlement_type 允许集仅 RECEIVE / PAY（应收核销 = RECEIVE）
        settlement.setSettlementType("RECEIVE");
        settlement.setSettlementDate(LocalDate.of(2026, 6, 29));
        settlement.setPeriod("202606");
        settlement.setPartyId(1L);
        settlement.setPartyType("CUSTOMER");
        settlement.setTotalAmount(new BigDecimal("5000.00"));
        settlement.setStatus("DRAFT");
        settlement.setDeleted(0);
        settlementMapper.insert(settlement);

        ArapSettlementEntryEntity entry = new ArapSettlementEntryEntity();
        entry.setSettlementId(settlement.getId());
        entry.setBusinessDocId(doc.getId());
        entry.setSettledAmount(new BigDecimal("5000.00"));
        entryMapper.insert(entry);

        doc.setSettledAmount(new BigDecimal("5000.00"));
        doc.setUnsettledAmount(new BigDecimal("6300.00"));
        businessDocMapper.updateById(doc);

        BusinessDocEntity updatedDoc = businessDocMapper.selectById(doc.getId());
        assertEquals(new BigDecimal("5000.00"), updatedDoc.getSettledAmount());
        assertEquals(new BigDecimal("6300.00"), updatedDoc.getUnsettledAmount());
    }

    @Test
    @DisplayName("核销单与业务单据关联: 应付核销场景")
    void settlement_with_payable_businessDoc() {
        String invoiceNo = "9999.E2E.SETTLE.PI.001";
        String docNo = "9999.E2E.SETTLE.PDOC.001";
        String settlementNo = "9999.E2E.SETTLE.PSET.001";

        InputInvoiceEntity invoice = new InputInvoiceEntity();
        invoice.setInvoiceNo(invoiceNo);
        invoice.setInvoiceDate(LocalDate.of(2026, 6, 28));
        invoice.setPeriod("202606");
        invoice.setAmount(new BigDecimal("10000.00"));
        invoice.setTaxAmount(new BigDecimal("1300.00"));
        invoice.setTotalAmount(new BigDecimal("11300.00"));
        invoice.setInvoiceType("SPECIAL");
        invoice.setTaxRate(new BigDecimal("0.13"));
        invoice.setCertificationStatus("UNCERTIFIED");
        invoice.setDeleted(0);
        inputInvoiceMapper.insert(invoice);

        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo(docNo);
        doc.setDocType("INVOICE_IN");
        doc.setPeriod("202606");
        doc.setDocDate(LocalDate.of(2026, 6, 28));
        doc.setAmount(new BigDecimal("11300.00"));
        doc.setInvoiceNo(invoiceNo);
        doc.setStatus("APPROVED");
        doc.setSupplierId(1L);
        doc.setUnsettledAmount(new BigDecimal("11300.00"));
        businessDocMapper.insert(doc);

        ArapSettlementEntity settlement = new ArapSettlementEntity();
        settlement.setSettlementNo(settlementNo);
        // chk_settlement_type 允许集仅 RECEIVE / PAY（应付核销 = PAY）
        settlement.setSettlementType("PAY");
        settlement.setSettlementDate(LocalDate.of(2026, 6, 29));
        settlement.setPeriod("202606");
        settlement.setPartyId(1L);
        settlement.setPartyType("VENDOR");
        settlement.setTotalAmount(new BigDecimal("8000.00"));
        settlement.setStatus("DRAFT");
        settlement.setDeleted(0);
        settlementMapper.insert(settlement);

        ArapSettlementEntryEntity entry = new ArapSettlementEntryEntity();
        entry.setSettlementId(settlement.getId());
        entry.setBusinessDocId(doc.getId());
        entry.setSettledAmount(new BigDecimal("8000.00"));
        entryMapper.insert(entry);

        doc.setSettledAmount(new BigDecimal("8000.00"));
        doc.setUnsettledAmount(new BigDecimal("3300.00"));
        businessDocMapper.updateById(doc);

        BusinessDocEntity updatedDoc = businessDocMapper.selectById(doc.getId());
        assertEquals(new BigDecimal("8000.00"), updatedDoc.getSettledAmount());
        assertEquals(new BigDecimal("3300.00"), updatedDoc.getUnsettledAmount());
    }
}