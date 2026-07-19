package com.huicai.sme.tax.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.sme.tax.entity.InputInvoiceEntity;
import com.huicai.sme.tax.entity.TaxDeclarationEntity;
import com.huicai.sme.tax.mapper.InputInvoiceMapper;
import com.huicai.sme.tax.mapper.OutputInvoiceMapper;
import com.huicai.sme.tax.mapper.TaxDeclarationMapper;
import com.huicai.sme.tax.mapper.TaxTypeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaxServiceImplTest {

    @Mock private TaxTypeMapper taxTypeMapper;
    @Mock private InputInvoiceMapper inputMapper;
    @Mock private OutputInvoiceMapper outputMapper;
    @Mock private TaxDeclarationMapper declarationMapper;
    @InjectMocks private TaxServiceImpl service;

    private InputInvoiceEntity stubInput(Long id, String status) {
        InputInvoiceEntity e = new InputInvoiceEntity();
        e.setId(id);
        e.setInvoiceNo("INV-2026-001");
        e.setTaxAmount(new BigDecimal("130.00"));
        e.setCertificationStatus(status);
        return e;
    }

    private TaxDeclarationEntity stubDecl(Long id, String status) {
        TaxDeclarationEntity d = new TaxDeclarationEntity();
        d.setId(id);
        d.setDeclarationNo("DECL-202606-001");
        d.setPeriod("202606");
        d.setPayableAmount(new BigDecimal("500.00"));
        d.setStatus(status);
        return d;
    }

    // ==================== certify ====================

    @Test
    void certify_uncertified_becomes_certified_with_deduction_amount() {
        when(inputMapper.selectById(1L)).thenReturn(stubInput(1L, "UNCERTIFIED"));
        InputInvoiceEntity r = service.certify(1L, "202606");
        assertEquals("CERTIFIED", r.getCertificationStatus());
        assertEquals(new BigDecimal("130.00"), r.getDeductionAmount());
        assertNotNull(r.getCertifiedDate());
        assertEquals("202606", r.getDeductionPeriod());
        verify(inputMapper).updateById(any(InputInvoiceEntity.class));
    }

    @Test
    void certify_already_certified_throws() {
        when(inputMapper.selectById(1L)).thenReturn(stubInput(1L, "CERTIFIED"));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.certify(1L, null));
        assertTrue(ex.getMessage().contains("不可认证"));
    }

    @Test
    void certify_invoice_not_found_throws() {
        when(inputMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.certify(99L, null));
        assertTrue(ex.getMessage().contains("发票不存在"));
    }

    // ==================== calculateVat ====================

    @Test
    void calculateVat_positive_payable_with_12pct_surcharge() {
        Map<String, Object> out = new HashMap<>();
        out.put("tax", new BigDecimal("1000.00"));
        when(outputMapper.summaryByPeriod("202606")).thenReturn(out);
        Map<String, Object> in = new HashMap<>();
        in.put("deductible", new BigDecimal("600.00"));
        when(inputMapper.summaryByPeriod("202606")).thenReturn(in);

        Map<String, Object> r = service.calculateVat("202606");
        assertEquals("202606", r.get("period"));
        assertEquals(new BigDecimal("1000.00"), r.get("outputTax"));
        assertEquals(new BigDecimal("600.00"), r.get("inputTax"));
        assertEquals(new BigDecimal("400.00"), r.get("payableTax"));
        // 附加税 = 400 * 0.12 = 48
        assertEquals(new BigDecimal("48.00"), r.get("surcharge"));
        assertEquals(new BigDecimal("448.00"), r.get("totalPayable"));
    }

    @Test
    void calculateVat_over_deduction_returns_zero_with_note() {
        Map<String, Object> out = new HashMap<>();
        out.put("tax", new BigDecimal("500.00"));
        when(outputMapper.summaryByPeriod("202606")).thenReturn(out);
        Map<String, Object> in = new HashMap<>();
        in.put("deductible", new BigDecimal("800.00"));
        when(inputMapper.summaryByPeriod("202606")).thenReturn(in);

        Map<String, Object> r = service.calculateVat("202606");
        assertEquals(new BigDecimal("-300.00"), r.get("payableTax"));
        assertEquals(new BigDecimal("0"), r.get("surcharge"));
        assertEquals(new BigDecimal("0"), r.get("totalPayable"));
        assertNotNull(r.get("note"));
        assertTrue(r.get("note").toString().contains("留抵"));
    }

    // ==================== inputSummary / outputByTaxRate ====================

    @Test
    void inputSummary_returns_map() {
        Map<String, Object> mock = new HashMap<>();
        mock.put("amount", new BigDecimal("1000"));
        mock.put("tax", new BigDecimal("130"));
        when(inputMapper.summaryByPeriod("202606")).thenReturn(mock);

        Map<String, Object> r = service.inputSummary("202606");
        assertEquals(2, r.size());
    }

    @Test
    void outputByTaxRate_returns_list() {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> a = new HashMap<>();
        a.put("rate", "13%"); a.put("amount", "1000"); a.put("tax", "130");
        list.add(a);
        Map<String, Object> b = new HashMap<>();
        b.put("rate", "6%"); b.put("amount", "500"); b.put("tax", "30");
        list.add(b);
        when(outputMapper.byTaxRate("202606")).thenReturn(list);

        List<Map<String, Object>> r = service.outputByTaxRate("202606");
        assertEquals(2, r.size());
    }

    // ==================== submitDeclaration ====================

    @Test
    void submitDeclaration_draft_becomes_submitted() {
        when(declarationMapper.selectById(1L)).thenReturn(stubDecl(1L, "DRAFT"));
        TaxDeclarationEntity r = service.submitDeclaration(1L);
        assertEquals("SUBMITTED", r.getStatus());
        verify(declarationMapper).updateById(any(TaxDeclarationEntity.class));
    }

    @Test
    void submitDeclaration_non_draft_throws() {
        when(declarationMapper.selectById(1L)).thenReturn(stubDecl(1L, "SUBMITTED"));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.submitDeclaration(1L));
        assertTrue(ex.getMessage().contains("仅草稿状态可提交"));
    }

    // ==================== P18-1: 申报审批 / 驳回 ====================

    @Test
    void approveDeclaration_submitted_becomes_approved() {
        when(declarationMapper.selectById(1L)).thenReturn(stubDecl(1L, "SUBMITTED"));
        TaxDeclarationEntity r = service.approveDeclaration(1L, "zhangsan");
        assertEquals("APPROVED", r.getStatus());
        verify(declarationMapper).updateById(any(TaxDeclarationEntity.class));
    }

    @Test
    void rejectDeclaration_submitted_becomes_rejected_with_reason() {
        when(declarationMapper.selectById(1L)).thenReturn(stubDecl(1L, "SUBMITTED"));
        TaxDeclarationEntity r = service.rejectDeclaration(1L, "lisi", "材料不齐");
        assertEquals("REJECTED", r.getStatus());
        assertNotNull(r.getRemark());
        assertTrue(r.getRemark().contains("材料不齐"));
    }

    @Test
    void rejectDeclaration_empty_reason_throws() {
        when(declarationMapper.selectById(1L)).thenReturn(stubDecl(1L, "SUBMITTED"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.rejectDeclaration(1L, "x", ""));
        assertTrue(ex.getMessage().contains("驳回必须填理由"));
    }
}
