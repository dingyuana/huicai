package com.huicai.module.arap.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.entity.ExpenseReimbursementEntity;
import com.huicai.module.arap.mapper.ExpenseReimbursementMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseReimbursementServiceImplTest {

    @Mock private ExpenseReimbursementMapper mapper;
    @InjectMocks private ExpenseReimbursementServiceImpl service;

    private ExpenseReimbursementEntity stub(Long id, String status) {
        ExpenseReimbursementEntity e = new ExpenseReimbursementEntity();
        e.setId(id);
        e.setReimbNo("REIMB-202606-0001");
        e.setEmployeeId(5L);
        e.setExpenseType("TRAVEL");
        e.setAmount(new BigDecimal("500.00"));
        e.setStatus(status);
        e.setSummary("差旅费");
        return e;
    }

    // ─── createDraft ───

    @Test
    void createDraft_员工ID为空_throw() {
        ExpenseReimbursementEntity e = stub(null, "DRAFT");
        e.setEmployeeId(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createDraft(e));
        assertTrue(ex.getMessage().contains("员工ID不能为空"));
    }

    @Test
    void createDraft_费用类型空_throw() {
        ExpenseReimbursementEntity e = stub(null, "DRAFT");
        e.setExpenseType(null);
        assertThrows(BusinessException.class, () -> service.createDraft(e));
    }

    @Test
    void createDraft_金额0_throw() {
        ExpenseReimbursementEntity e = stub(null, "DRAFT");
        e.setAmount(BigDecimal.ZERO);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createDraft(e));
        assertTrue(ex.getMessage().contains("金额必须大于0"));
    }

    @Test
    void createDraft_正常_插入并返回() {
        ExpenseReimbursementEntity e = stub(null, "DRAFT");
        when(mapper.insert(any(ExpenseReimbursementEntity.class))).thenReturn(1);
        ExpenseReimbursementEntity r = service.createDraft(e);
        assertEquals("DRAFT", r.getStatus());
        assertNotNull(r.getReimbNo());
        assertTrue(r.getReimbNo().startsWith("REIMB-"));
        verify(mapper).insert(any(ExpenseReimbursementEntity.class));
    }

    // ─── updateDraft ───

    @Test
    void updateDraft_非DRAFT_throw() {
        when(mapper.selectById(1L)).thenReturn(stub(1L, "SUBMITTED"));
        ExpenseReimbursementEntity e = stub(1L, "SUBMITTED");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateDraft(e));
        assertTrue(ex.getMessage().contains("仅 DRAFT 状态可修改"));
    }

    @Test
    void updateDraft_正常_更新字段() {
        when(mapper.selectById(1L)).thenReturn(stub(1L, "DRAFT"));
        ExpenseReimbursementEntity e = stub(1L, "DRAFT");
        e.setAmount(new BigDecimal("800.00"));
        service.updateDraft(e);
        verify(mapper).updateById(any(ExpenseReimbursementEntity.class));
    }

    // ─── 状态机: DRAFT → SUBMITTED ───

    @Test
    void submit_DRAFT_变SUBMITTED() {
        when(mapper.selectById(1L)).thenReturn(stub(1L, "DRAFT"));
        ExpenseReimbursementEntity r = service.submit(1L);
        assertEquals("SUBMITTED", r.getStatus());
        assertNotNull(r.getSubmittedAt());
    }

    @Test
    void submit_非DRAFT_throw() {
        when(mapper.selectById(1L)).thenReturn(stub(1L, "APPROVED"));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.submit(1L));
        assertTrue(ex.getMessage().contains("仅 DRAFT 可提交"));
    }

    // ─── 状态机: SUBMITTED → APPROVED ───

    @Test
    void approve_SUBMITTED_变APPROVED() {
        when(mapper.selectById(1L)).thenReturn(stub(1L, "SUBMITTED"));
        ExpenseReimbursementEntity r = service.approve(1L, "zhangsan");
        assertEquals("APPROVED", r.getStatus());
        assertEquals("zhangsan", r.getApprovedBy());
    }

    @Test
    void approve_非SUBMITTED_throw() {
        when(mapper.selectById(1L)).thenReturn(stub(1L, "DRAFT"));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(1L, "x"));
        assertTrue(ex.getMessage().contains("仅 SUBMITTED"));
    }

    // ─── 状态机: SUBMITTED → REJECTED ───

    @Test
    void reject_理由空_throw() {
        when(mapper.selectById(1L)).thenReturn(stub(1L, "SUBMITTED"));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.reject(1L, "x", ""));
        assertTrue(ex.getMessage().contains("驳回必须填理由"));
    }

    @Test
    void reject_正常_变REJECTED() {
        when(mapper.selectById(1L)).thenReturn(stub(1L, "SUBMITTED"));
        ExpenseReimbursementEntity r = service.reject(1L, "lisi", "金额不合理");
        assertEquals("REJECTED", r.getStatus());
        assertEquals("金额不合理", r.getRejectReason());
    }

    // ─── 状态机: APPROVED → VOUCHERED ───

    @Test
    void generateVoucher_APPROVED_变VOUCHERED() {
        when(mapper.selectById(1L)).thenReturn(stub(1L, "APPROVED"));
        ExpenseReimbursementEntity r = service.generateVoucher(1L, 999L);
        assertEquals("VOUCHERED", r.getStatus());
        assertEquals(999L, r.getVoucherId());
    }

    // ─── P11-3: 银行流水防重 ───

    @Test
    void autoCreateForBankStmt_已存在_返回旧单() {
        when(mapper.selectList(any())).thenReturn(List.of(stub(1L, "DRAFT")));
        ExpenseReimbursementEntity r = service.autoCreateForBankStmt(100L, 5L, new BigDecimal("500"), "差旅");
        assertEquals(1L, r.getId());
        verify(mapper, never()).insert(any(ExpenseReimbursementEntity.class));
    }

    @Test
    void autoCreateForBankStmt_新_插入草稿() {
        when(mapper.selectList(any())).thenReturn(List.of());
        when(mapper.insert(any(ExpenseReimbursementEntity.class))).thenReturn(1);
        ExpenseReimbursementEntity r = service.autoCreateForBankStmt(100L, 5L, new BigDecimal("500"), "差旅");
        assertEquals("DRAFT", r.getStatus());
        assertEquals("OTHER", r.getExpenseType());
        assertEquals(100L, r.getBankStmtId());
    }
}
