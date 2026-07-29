package com.huicai.base.voucher.service.impl;

import com.huicai.base.business.entity.ArapSettlementEntity;
import com.huicai.base.business.entity.ArapSettlementEntryEntity;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.business.entity.InputInvoiceEntity;
import com.huicai.base.business.entity.OutputInvoiceEntity;
import com.huicai.base.business.mapper.ArapSettlementEntryMapper;
import com.huicai.base.business.mapper.ArapSettlementMapper;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.business.mapper.InputInvoiceMapper;
import com.huicai.base.business.mapper.OutputInvoiceMapper;
import com.huicai.base.voucher.dto.NumberingTraceVO;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.voucher.service.VoucherService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("编号关联追溯服务测试")
class NumberingTraceServiceImplTest {

    @InjectMocks
    private NumberingTraceServiceImpl numberingTraceService;

    @Mock
    private VoucherMapper voucherMapper;
    @Mock
    private BusinessDocMapper businessDocMapper;
    @Mock
    private InputInvoiceMapper inputInvoiceMapper;
    @Mock
    private OutputInvoiceMapper outputInvoiceMapper;
    @Mock
    private ArapSettlementMapper arapSettlementMapper;
    @Mock
    private ArapSettlementEntryMapper settlementEntryMapper;
    @Mock
    private VoucherService voucherService;

    private static final LocalDateTime NOW = LocalDateTime.now();

    // ==================== 测试数据工厂 ====================

    private VoucherEntity createVoucher(String voucherNo, String status, String summary,
                                        String sourceDocType, String sourceDocNo) {
        VoucherEntity v = new VoucherEntity();
        v.setVoucherNo(voucherNo);
        v.setStatus(status);
        v.setSummary(summary);
        v.setSourceDocType(sourceDocType);
        v.setSourceDocNo(sourceDocNo);
        v.setCreatedAt(NOW);
        return v;
    }

    private BusinessDocEntity createDoc(String docNo, String docType, String voucherNo,
                                        String invoiceNo, String status, String summary, BigDecimal amount) {
        BusinessDocEntity d = new BusinessDocEntity();
        d.setId(1L);
        d.setDocNo(docNo);
        d.setDocType(docType);
        d.setVoucherNo(voucherNo);
        d.setInvoiceNo(invoiceNo);
        d.setStatus(status);
        d.setSummary(summary);
        d.setAmount(amount);
        d.setCreatedAt(NOW);
        return d;
    }

    private InputInvoiceEntity createInputInvoice(String invoiceNo, String vendorName,
                                                  BigDecimal totalAmount, String certificationStatus) {
        InputInvoiceEntity inv = new InputInvoiceEntity();
        inv.setInvoiceNo(invoiceNo);
        inv.setVendorName(vendorName);
        inv.setTotalAmount(totalAmount);
        inv.setCertificationStatus(certificationStatus);
        inv.setCreatedAt(NOW);
        return inv;
    }

    private ArapSettlementEntity createSettlement(String settlementNo, String settlementType,
                                                  BigDecimal totalAmount, String status, String voucherNo) {
        ArapSettlementEntity s = new ArapSettlementEntity();
        s.setId(1L);
        s.setSettlementNo(settlementNo);
        s.setSettlementType(settlementType);
        s.setTotalAmount(totalAmount);
        s.setStatus(status);
        s.setVoucherNo(voucherNo);
        s.setCreatedAt(NOW);
        return s;
    }

    // ==================== 测试用例 ====================

    @Test
    @DisplayName("traceByNumber: 空编号抛 IllegalArgumentException")
    void traceByNumber_blankNumber_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> numberingTraceService.traceByNumber(null));
        assertThrows(IllegalArgumentException.class, () -> numberingTraceService.traceByNumber(""));
        assertThrows(IllegalArgumentException.class, () -> numberingTraceService.traceByNumber("   "));
    }

    @Test
    @DisplayName("traceByNumber: 按凭证号匹配成功（VOUCHER 类型）")
    void traceByNumber_matchesVoucher_returnsVoucherType() {
        // Arrange
        VoucherEntity voucher = createVoucher("PZ-2024-001", "AUDITED", "采购付款", null, null);
        when(voucherMapper.selectOne(any())).thenReturn(voucher);

        // Act
        NumberingTraceVO result = numberingTraceService.traceByNumber("PZ-2024-001");

        // Assert
        assertNotNull(result);
        assertEquals("PZ-2024-001", result.getTraceNo());
        assertEquals("VOUCHER", result.getTraceType());
        // 下游：凭证无下游，但当前节点会加入列表
        assertEquals(1, result.getDownstream().size());
        assertEquals("VOUCHER", result.getDownstream().get(0).getNodeType());
        assertEquals("PZ-2024-001", result.getDownstream().get(0).getVoucherNo());
        // 上游：无 sourceDocNo，无上游
        assertTrue(result.getUpstream().isEmpty());
    }

    @Test
    @DisplayName("traceByNumber: 按业务单据号匹配成功（BUSINESS_DOC 类型）")
    void traceByNumber_matchesBusinessDoc_returnsBusinessDocType() {
        // Arrange
        BusinessDocEntity doc = createDoc("BD-001", "INVOICE_IN", "PZ-2024-001",
                "FJ-001", "CONFIRMED", "采购入库", BigDecimal.valueOf(5000));
        VoucherEntity voucher = createVoucher("PZ-2024-001", "AUDITED", "采购付款", null, null);
        InputInvoiceEntity invoice = createInputInvoice("FJ-001", "供应商A", BigDecimal.valueOf(5000), "CONFIRMED");

        // tryMatchByNumber: voucher 不匹配，businessDoc 匹配
        // buildVoucherForDoc: 查 businessDoc → voucher
        // buildDocUpstream: 查 inputInvoice
        when(voucherMapper.selectOne(any())).thenReturn(null, voucher);
        when(businessDocMapper.selectOne(any())).thenReturn(doc);
        when(inputInvoiceMapper.selectOne(any())).thenReturn(invoice);

        // Act
        NumberingTraceVO result = numberingTraceService.traceByNumber("BD-001");

        // Assert
        assertNotNull(result);
        assertEquals("BD-001", result.getTraceNo());
        assertEquals("BUSINESS_DOC", result.getTraceType());

        // 下游：[当前业务单据节点, 凭证节点]
        assertEquals(2, result.getDownstream().size());
        assertEquals("BUSINESS_DOC", result.getDownstream().get(0).getNodeType());
        assertEquals("BD-001", result.getDownstream().get(0).getNodeNo());
        assertEquals("VOUCHER", result.getDownstream().get(1).getNodeType());
        assertEquals("PZ-2024-001", result.getDownstream().get(1).getVoucherNo());

        // 上游：[发票节点]
        assertEquals(1, result.getUpstream().size());
        assertEquals("INPUT_INVOICE", result.getUpstream().get(0).getNodeType());
        assertEquals("FJ-001", result.getUpstream().get(0).getNodeNo());
    }

    @Test
    @DisplayName("traceByNumber: 按进项发票号匹配成功（INPUT_INVOICE 类型）")
    void traceByNumber_matchesInputInvoice_returnsInputInvoiceType() {
        // Arrange
        InputInvoiceEntity invoice = createInputInvoice("FJ-001", "供应商A", BigDecimal.valueOf(5000), "CONFIRMED");
        BusinessDocEntity doc = createDoc("BD-001", "INVOICE_IN", "PZ-2024-001",
                "FJ-001", "CONFIRMED", "采购入库", BigDecimal.valueOf(5000));

        // tryMatchByNumber: voucher 不匹配 → businessDoc 不匹配 → inputInvoice 匹配
        // buildNextDownstreamForInvoice: 查 businessDoc 按 invoiceNo + INVOICE_IN
        when(voucherMapper.selectOne(any())).thenReturn(null);
        when(businessDocMapper.selectOne(any())).thenReturn(null, doc);
        when(inputInvoiceMapper.selectOne(any())).thenReturn(invoice);

        // Act
        NumberingTraceVO result = numberingTraceService.traceByNumber("FJ-001");

        // Assert
        assertNotNull(result);
        assertEquals("FJ-001", result.getTraceNo());
        assertEquals("INPUT_INVOICE", result.getTraceType());

        // 下游：[当前发票节点, 业务单据节点]
        assertEquals(2, result.getDownstream().size());
        assertEquals("INPUT_INVOICE", result.getDownstream().get(0).getNodeType());
        assertEquals("FJ-001", result.getDownstream().get(0).getNodeNo());
        assertEquals("BUSINESS_DOC", result.getDownstream().get(1).getNodeType());
        assertEquals("BD-001", result.getDownstream().get(1).getNodeNo());
        assertEquals("PZ-2024-001", result.getDownstream().get(1).getVoucherNo());

        // 上游：进项发票无上游
        assertTrue(result.getUpstream().isEmpty());
    }

    @Test
    @DisplayName("traceByNumber: 按核销单号匹配成功（SETTLEMENT 类型）")
    void traceByNumber_matchesSettlement_returnsSettlementType() {
        // Arrange
        ArapSettlementEntity settlement = createSettlement("HX-001", "MANUAL",
                BigDecimal.valueOf(5000), "CONFIRMED", "PZ-2024-001");
        VoucherEntity voucher = createVoucher("PZ-2024-001", "AUDITED", "核销生成凭证", null, null);
        ArapSettlementEntryEntity entry = new ArapSettlementEntryEntity();
        entry.setSettlementId(1L);
        entry.setBusinessDocId(1L);
        entry.setSettledAmount(BigDecimal.valueOf(5000));
        entry.setCreatedAt(NOW);
        BusinessDocEntity doc = createDoc("BD-001", "INVOICE_IN", "PZ-2024-001",
                "FJ-001", "CONFIRMED", "采购入库", BigDecimal.valueOf(5000));

        // tryMatchByNumber: 前三个 mapper 都不匹配
        // buildVoucherForSettlement: 查 settlement → voucher
        // buildSettlementUpstream: 查 settlement → entry → businessDoc
        when(voucherMapper.selectOne(any())).thenReturn(null, voucher);
        when(businessDocMapper.selectOne(any())).thenReturn(null, doc);
        when(inputInvoiceMapper.selectOne(any())).thenReturn(null);
        when(arapSettlementMapper.selectOne(any())).thenReturn(settlement);
        when(settlementEntryMapper.selectOne(any())).thenReturn(entry);

        // Act
        NumberingTraceVO result = numberingTraceService.traceByNumber("HX-001");

        // Assert
        assertNotNull(result);
        assertEquals("HX-001", result.getTraceNo());
        assertEquals("SETTLEMENT", result.getTraceType());

        // 下游：[当前核销单节点, 凭证节点]
        assertEquals(2, result.getDownstream().size());
        assertEquals("SETTLEMENT", result.getDownstream().get(0).getNodeType());
        assertEquals("HX-001", result.getDownstream().get(0).getNodeNo());
        assertEquals("VOUCHER", result.getDownstream().get(1).getNodeType());
        assertEquals("PZ-2024-001", result.getDownstream().get(1).getVoucherNo());

        // 上游：[业务单据节点]
        assertEquals(1, result.getUpstream().size());
        assertEquals("BUSINESS_DOC", result.getUpstream().get(0).getNodeType());
        assertEquals("BD-001", result.getUpstream().get(0).getNodeNo());
    }

    @Test
    @DisplayName("traceByNumber: 无匹配返回 UNKNOWN 类型")
    void traceByNumber_noMatch_returnsUnknownType() {
        // Arrange
        when(voucherMapper.selectOne(any())).thenReturn(null);
        when(businessDocMapper.selectOne(any())).thenReturn(null);
        when(inputInvoiceMapper.selectOne(any())).thenReturn(null);
        when(arapSettlementMapper.selectOne(any())).thenReturn(null);

        // Act
        NumberingTraceVO result = numberingTraceService.traceByNumber("NO-EXIST-001");

        // Assert
        assertNotNull(result);
        assertEquals("NO-EXIST-001", result.getTraceNo());
        assertEquals("UNKNOWN", result.getTraceType());
        assertTrue(result.getUpstream().isEmpty());
        assertTrue(result.getDownstream().isEmpty());
    }

    @Test
    @DisplayName("traceByNumber: 下游链路 — 发票 → 业务单据 → 凭证")
    void traceByNumber_downstreamChain_invoiceToDocToVoucher() {
        // Arrange
        InputInvoiceEntity invoice = createInputInvoice("FJ-001", "供应商A", BigDecimal.valueOf(5000), "CONFIRMED");
        BusinessDocEntity doc = createDoc("BD-001", "INVOICE_IN", "PZ-2024-001",
                "FJ-001", "CONFIRMED", "采购入库", BigDecimal.valueOf(5000));
        VoucherEntity voucher = createVoucher("PZ-2024-001", "AUDITED", "采购付款", null, null);

        // tryMatchByNumber: voucher 不匹配 → businessDoc 不匹配 → inputInvoice 匹配
        // buildNextDownstreamForInvoice: 查 businessDoc(invoiceNo + INVOICE_IN) → 返回 docNode
        // buildVoucherForDoc 不会在此路径中被调用（INPUT_INVOICE 分支不触发）
        // 但 buildNextDownstreamForInvoice 返回的 docNode 包含 voucherNo
        when(voucherMapper.selectOne(any())).thenReturn(null);
        when(businessDocMapper.selectOne(any())).thenReturn(null, doc);
        when(inputInvoiceMapper.selectOne(any())).thenReturn(invoice);

        // Act
        NumberingTraceVO result = numberingTraceService.traceByNumber("FJ-001");

        // Assert
        assertNotNull(result);
        assertEquals("INPUT_INVOICE", result.getTraceType());

        // downstream 应包含：[发票节点, 业务单据节点]
        assertEquals(2, result.getDownstream().size());
        assertEquals("INPUT_INVOICE", result.getDownstream().get(0).getNodeType());
        assertEquals("FJ-001", result.getDownstream().get(0).getNodeNo());
        assertEquals("进项发票: 供应商A", result.getDownstream().get(0).getSummary());

        assertEquals("BUSINESS_DOC", result.getDownstream().get(1).getNodeType());
        assertEquals("BD-001", result.getDownstream().get(1).getNodeNo());
        assertEquals("PZ-2024-001", result.getDownstream().get(1).getVoucherNo());

        // 验证金额逐级传递
        assertEquals(0, BigDecimal.valueOf(5000).compareTo(result.getDownstream().get(0).getAmount()));
        assertEquals(0, BigDecimal.valueOf(5000).compareTo(result.getDownstream().get(1).getAmount()));
    }

    @Test
    @DisplayName("traceByNumber: 上游链路 — 凭证 → 业务单据")
    void traceByNumber_upstreamChain_voucherToBusinessDoc() {
        // Arrange
        VoucherEntity voucher = createVoucher("PZ-2024-001", "AUDITED", "采购付款",
                "BUSINESS_DOC", "BD-001");
        BusinessDocEntity doc = createDoc("BD-001", "INVOICE_IN", "PZ-2024-001",
                "FJ-001", "CONFIRMED", "采购入库", BigDecimal.valueOf(5000));

        // tryMatchByNumber: voucher 匹配
        // buildNextUpstreamForVoucher: 查 voucher → sourceDocType=BUSINESS_DOC → findDocByNo
        when(voucherMapper.selectOne(any())).thenReturn(voucher);
        when(businessDocMapper.selectOne(any())).thenReturn(doc);

        // Act
        NumberingTraceVO result = numberingTraceService.traceByNumber("PZ-2024-001");

        // Assert
        assertNotNull(result);
        assertEquals("PZ-2024-001", result.getTraceNo());
        assertEquals("VOUCHER", result.getTraceType());

        // 上游：[业务单据节点]
        assertEquals(1, result.getUpstream().size());
        assertEquals("BUSINESS_DOC", result.getUpstream().get(0).getNodeType());
        assertEquals("BD-001", result.getUpstream().get(0).getNodeNo());
        assertEquals("PZ-2024-001", result.getUpstream().get(0).getVoucherNo());

        // 下游：只有当前凭证节点
        assertEquals(1, result.getDownstream().size());
        assertEquals("VOUCHER", result.getDownstream().get(0).getNodeType());
    }
}