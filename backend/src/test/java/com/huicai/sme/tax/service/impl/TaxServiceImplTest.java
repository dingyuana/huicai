package com.huicai.sme.tax.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.base.business.entity.InputInvoiceEntity;
import com.huicai.base.business.entity.OutputInvoiceEntity;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.voucher.service.VoucherNoService;
import com.huicai.sme.tax.dto.BatchOperationResult;
import com.huicai.sme.tax.entity.TaxDeclarationEntity;
import com.huicai.base.business.mapper.InputInvoiceMapper;
import com.huicai.base.business.mapper.OutputInvoiceMapper;
import com.huicai.base.business.service.OutputInvoiceStateMachineService;
import com.huicai.base.business.util.TemplateMatcher;
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
    @Mock private OutputInvoiceStateMachineService stateMachine;
    @Mock private VoucherMapper voucherMapper;
    @Mock private VoucherEntryMapper voucherEntryMapper;
    @Mock private VoucherNoService voucherNoService;
    @Mock private SubjectMapper subjectMapper;
    @Mock private TemplateMatcher templateMatcher;
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
        InputInvoiceEntity stub = stubInput(1L, "UNCERTIFIED");
        when(inputMapper.selectById(1L)).thenReturn(stub);
        InputInvoiceEntity r = service.certify(1L, "202606");
        assertEquals("CERTIFIED", r.getCertificationStatus());
        assertEquals("UNDECLARED", r.getDeclaredStatus());
        assertEquals(new BigDecimal("130.00"), r.getDeductionAmount());
        assertNotNull(r.getCertifiedDate());
        assertEquals("202606", r.getDeductionPeriod());
        verify(inputMapper).updateById(any(InputInvoiceEntity.class));
    }

    // ==================== declareDeduction (P57) ====================

    @Test
    void declareDeduction_certified_undeclared_becomes_declared() {
        InputInvoiceEntity stub = stubInput(1L, "CERTIFIED");
        stub.setDeclaredStatus("UNDECLARED");
        stub.setTaxAmount(new BigDecimal("130.00"));
        when(inputMapper.selectById(1L)).thenReturn(stub);
        InputInvoiceEntity r = service.declareDeduction(1L, "202607", 100L);
        assertEquals("DECLARED", r.getDeclaredStatus());
        assertEquals("202607", r.getDeclaredPeriod());
        assertNotNull(r.getDeclaredDate());
        verify(inputMapper).updateById(any(InputInvoiceEntity.class));
    }

    @Test
    void declareDeduction_uncertified_throws() {
        InputInvoiceEntity stub = stubInput(1L, "UNCERTIFIED");
        stub.setDeclaredStatus("UNDECLARED");
        when(inputMapper.selectById(1L)).thenReturn(stub);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.declareDeduction(1L, "202607", 100L));
        assertTrue(ex.getMessage().contains("仅已认证"));
    }

    @Test
    void declareDeduction_already_declared_throws() {
        InputInvoiceEntity stub = stubInput(1L, "CERTIFIED");
        stub.setDeclaredStatus("DECLARED");
        when(inputMapper.selectById(1L)).thenReturn(stub);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.declareDeduction(1L, "202607", 100L));
        assertTrue(ex.getMessage().contains("已申报抵扣"));
    }

    @Test
    void declareDeduction_invoice_not_found_throws() {
        when(inputMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.declareDeduction(99L, "202607", 100L));
        assertTrue(ex.getMessage().contains("发票不存在"));
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
    void calculateVat_declared_only_counts_declared() {
        // 认证未申报不计入抵扣：已认证未申报 1000 + 已申报 500 + 销项 2000
        Map<String, Object> out = new HashMap<>();
        out.put("tax", new BigDecimal("2000.00"));
        when(outputMapper.summaryByPeriod("202608")).thenReturn(out);
        Map<String, Object> in = new HashMap<>();
        in.put("deductible", new BigDecimal("500.00"));   // 仅已申报
        when(inputMapper.summaryByPeriod("202608")).thenReturn(in);

        Map<String, Object> r = service.calculateVat("202608");
        assertEquals(new BigDecimal("500.00"), r.get("inputTax"));
        assertEquals(new BigDecimal("1500.00"), r.get("payableTax"));
    }

    @Test
    void calculateVat_all_certified_undeclared_input_is_zero() {
        Map<String, Object> out = new HashMap<>();
        out.put("tax", new BigDecimal("800.00"));
        when(outputMapper.summaryByPeriod("202608")).thenReturn(out);
        Map<String, Object> in = new HashMap<>();
        in.put("deductible", new BigDecimal("0"));   // 全部认证未申报
        when(inputMapper.summaryByPeriod("202608")).thenReturn(in);

        Map<String, Object> r = service.calculateVat("202608");
        assertEquals(new BigDecimal("0"), r.get("inputTax"));
        assertEquals(new BigDecimal("800.00"), r.get("payableTax"));
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

    // ==================== P56 销项发票批量操作 ====================

    @Test
    void batchSubmitForReview_allSuccess() {
        List<Long> ids = List.of(1L, 2L, 3L);
        BatchOperationResult r = service.batchSubmitForReview(ids, 100L);
        assertEquals(3, r.getSuccess().size());
        assertEquals(0, r.getFailure().size());
        verify(stateMachine, times(3)).submitForReview(anyLong(), anyLong());
    }

    @Test
    void batchSubmitForReview_partialFailure_businessException() {
        doNothing().when(stateMachine).submitForReview(eq(1L), anyLong());
        doThrow(BusinessException.badRequest("仅待确认状态可提交审核，当前: VOUCHERED"))
                .when(stateMachine).submitForReview(eq(2L), anyLong());
        doNothing().when(stateMachine).submitForReview(eq(3L), anyLong());

        BatchOperationResult r = service.batchSubmitForReview(List.of(1L, 2L, 3L), 100L);

        assertEquals(2, r.getSuccess().size());
        assertTrue(r.getSuccess().contains(1L) && r.getSuccess().contains(3L));
        assertEquals(1, r.getFailure().size());
        assertEquals(2L, r.getFailure().get(0).getId());
        assertTrue(r.getFailure().get(0).getReason().contains("仅待确认状态"));
    }

    @Test
    void batchSubmitForReview_partialFailure_systemException() {
        doNothing().when(stateMachine).submitForReview(eq(1L), anyLong());
        doThrow(new RuntimeException("DB connection lost"))
                .when(stateMachine).submitForReview(eq(2L), anyLong());

        BatchOperationResult r = service.batchSubmitForReview(List.of(1L, 2L), 100L);

        assertEquals(1, r.getSuccess().size());
        assertEquals(1, r.getFailure().size());
        assertEquals(2L, r.getFailure().get(0).getId());
        assertTrue(r.getFailure().get(0).getReason().contains("系统异常"));
    }

    @Test
    void batchReject_emptyReason_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.batchReject(List.of(1L), 100L, ""));
        assertTrue(ex.getMessage().contains("驳回必须填写原因"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void batchVoid_emptyReason_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.batchVoid(List.of(1L), 100L, null));
        assertTrue(ex.getMessage().contains("作废必须填写原因"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void batchReverse_emptyReason_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.batchReverse(List.of(1L), 100L, "   "));
        assertTrue(ex.getMessage().contains("红冲必须填写原因"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void batchConfirm_allSuccess() {
        BatchOperationResult r = service.batchConfirm(List.of(1L, 2L), 100L);
        assertEquals(2, r.getSuccess().size());
        verify(stateMachine, times(2)).confirm(anyLong(), anyLong());
    }

    @Test
    void batchRevert_allSuccess() {
        BatchOperationResult r = service.batchRevert(List.of(1L), 100L);
        assertEquals(1, r.getSuccess().size());
        verify(stateMachine).revertToReview(eq(1L), anyLong());
    }

    @Test
    void batchReject_allSuccess_withReason() {
        BatchOperationResult r = service.batchReject(List.of(1L, 2L), 100L, "材料不齐");
        assertEquals(2, r.getSuccess().size());
        verify(stateMachine).reject(eq(1L), eq(100L), eq("材料不齐"));
        verify(stateMachine).reject(eq(2L), eq(100L), eq("材料不齐"));
    }

    @Test
    void batchVoid_allSuccess_withReason() {
        BatchOperationResult r = service.batchVoid(List.of(1L), 100L, "重复开票");
        assertEquals(1, r.getSuccess().size());
        verify(stateMachine).voidInvoice(eq(1L), eq(100L), eq("重复开票"));
    }

    @Test
    void batchReverse_allSuccess_withReason_returnsNullFromStateMachine() {
        when(stateMachine.reverseInvoice(eq(1L), anyLong(), anyString())).thenReturn(99L);
        BatchOperationResult r = service.batchReverse(List.of(1L), 100L, "客户退货");
        assertEquals(1, r.getSuccess().size());
        verify(stateMachine).reverseInvoice(eq(1L), eq(100L), eq("客户退货"));
    }

    @Test
    void batchSubmitForReview_emptyList_returnsEmptyResult() {
        BatchOperationResult r = service.batchSubmitForReview(List.of(), 100L);
        assertEquals(0, r.getSuccess().size());
        assertEquals(0, r.getFailure().size());
        verify(stateMachine, never()).submitForReview(anyLong(), anyLong());
    }

    // ==================== P33 红字发票凭证生成（chk_entry_amount 约束） ====================

    private OutputInvoiceEntity stubOutputInvoice(Long id, String status, String amount, String tax, String total) {
        OutputInvoiceEntity e = new OutputInvoiceEntity();
        e.setId(id);
        e.setInvoiceNo("OUT-2026-" + id);
        e.setCustomerName("测试客户");
        e.setAmount(new BigDecimal(amount));
        e.setTaxAmount(new BigDecimal(tax));
        e.setTotalAmount(new BigDecimal(total));
        e.setPeriod("202608");
        e.setStatus(status);
        return e;
    }

    private Subject subject(Long id, String code) {
        Subject s = new Subject();
        s.setId(id);
        s.setCode(code);
        return s;
    }

    @Test
    void generateVoucherFromInvoice_blueInvoice_entries_positive() {
        OutputInvoiceEntity inv = stubOutputInvoice(1L, "CONFIRMED", "1000.00", "130.00", "1130.00");
        when(outputMapper.selectById(1L)).thenReturn(inv);
        when(templateMatcher.match(any())).thenReturn(null);
        when(voucherNoService.generateNextNo(anyString(), anyLong())).thenReturn("记-202608-0001");
        when(subjectMapper.selectList(any())).thenReturn(List.of(subject(101L, "1122"), subject(102L, "5001"), subject(103L, "2221.01")));

        service.generateVoucherFromInvoice(1L, 100L);

        verify(voucherEntryMapper, times(3)).insert(any(VoucherEntryEntity.class));
        assertEquals(new BigDecimal("1130.00"), inv.getTotalAmount());
        assertEquals("VOUCHERED", inv.getStatus());
    }

    @Test
    void generateVoucherFromInvoice_redInvoice_entries_all_positive_and_reversed() {
        OutputInvoiceEntity inv = stubOutputInvoice(5L, "CONFIRMED", "-1000.00", "-130.00", "-1130.00");
        when(outputMapper.selectById(5L)).thenReturn(inv);
        when(templateMatcher.match(any())).thenReturn(null);
        when(voucherNoService.generateNextNo(anyString(), anyLong())).thenReturn("记-202608-0002");
        when(subjectMapper.selectList(any())).thenReturn(List.of(subject(101L, "1122"), subject(102L, "5001"), subject(103L, "2221.01")));

        List<VoucherEntryEntity> inserted = new ArrayList<>();
        doAnswer(a -> { inserted.add(a.getArgument(0)); return 1; })
                .when(voucherEntryMapper).insert(any(VoucherEntryEntity.class));

        service.generateVoucherFromInvoice(5L, 100L);

        // 红字发票：应收在贷方(1130)、收入在借方(1000)、销项税在借方(130)，金额全部为正
        assertEquals(3, inserted.size(), "红字发票应生成 3 条分录");
        Map<Long, VoucherEntryEntity> bySubject = new HashMap<>();
        for (VoucherEntryEntity ve : inserted) bySubject.put(ve.getSubjectId(), ve);

        VoucherEntryEntity ar = bySubject.get(101L);   // 1122 应收账款
        assertNotNull(ar, "应收分录缺失");
        assertEquals(new BigDecimal("1130.00"), ar.getCredit());
        assertEquals(0, ar.getDebit().compareTo(BigDecimal.ZERO));

        VoucherEntryEntity rev = bySubject.get(102L);  // 5001 主营业务收入
        assertNotNull(rev, "收入分录缺失");
        assertEquals(new BigDecimal("1000.00"), rev.getDebit());
        assertEquals(0, rev.getCredit().compareTo(BigDecimal.ZERO));

        VoucherEntryEntity tax = bySubject.get(103L);  // 2221.01 销项税
        assertNotNull(tax, "销项税分录缺失");
        assertEquals(new BigDecimal("130.00"), tax.getDebit());
        assertEquals(0, tax.getCredit().compareTo(BigDecimal.ZERO));

        // 所有分录必须满足 chk_entry_amount 约束：debit >= 0 && credit >= 0
        for (VoucherEntryEntity ve : inserted) {
            assertTrue(ve.getDebit().compareTo(BigDecimal.ZERO) >= 0, "借方不能为负: " + ve.getSubjectId());
            assertTrue(ve.getCredit().compareTo(BigDecimal.ZERO) >= 0, "贷方不能为负: " + ve.getSubjectId());
        }
        assertEquals("VOUCHERED", inv.getStatus());
    }
}
