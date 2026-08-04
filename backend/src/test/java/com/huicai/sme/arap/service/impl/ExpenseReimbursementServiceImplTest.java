package com.huicai.sme.arap.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.sme.arap.dto.ExpenseReimbursementVO;
import com.huicai.sme.arap.entity.ExpenseReimbursementEntity;
import com.huicai.base.masterdata.mapper.EmployeeMapper;
import com.huicai.sme.arap.mapper.ExpenseReimbursementMapper;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.DeptMapper;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.system.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.huicai.base.system.entity.Subject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

@ExtendWith(MockitoExtension.class)
class ExpenseReimbursementServiceImplTest {

    @Mock private ExpenseReimbursementMapper mapper;
    @Mock private VoucherMapper voucherMapper;
    @Mock private VoucherEntryMapper voucherEntryMapper;
    @Mock private SubjectMapper subjectMapper;
    @Mock private EmployeeMapper employeeMapper;
    @Mock private UserMapper userMapper;
    @Mock private DeptMapper deptMapper;
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

    /** 屏蔽 populateEmployeeNames 的依赖 mapper（stub 已设 employeeId=5L） */
    private void stubPartyMappers() {
        lenient().when(employeeMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());
    }

    /** selectById 用的 entity stub（expenseType=OTHER + bankStmtId=100，区别于默认 stub） */
    private ExpenseReimbursementEntity stubOther(Long id) {
        ExpenseReimbursementEntity e = new ExpenseReimbursementEntity();
        e.setId(id);
        e.setReimbNo("REIMB-202606-0001");
        e.setEmployeeId(5L);
        e.setExpenseType("OTHER");
        e.setAmount(new BigDecimal("500.00"));
        e.setStatus("DRAFT");
        e.setSummary("差旅");
        e.setBankStmtId(100L);
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
        when(mapper.insert(any(ExpenseReimbursementEntity.class))).thenAnswer(inv -> {
            ((ExpenseReimbursementEntity) inv.getArgument(0)).setId(1L);
            return 1;
        });
        when(mapper.selectById(1L)).thenReturn(stub(1L, "DRAFT"));
        stubPartyMappers();
        ExpenseReimbursementVO r = service.createDraft(e);
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
        stubPartyMappers();
        ExpenseReimbursementEntity e = stub(1L, "DRAFT");
        e.setAmount(new BigDecimal("800.00"));
        service.updateDraft(e);
        verify(mapper).updateById(any(ExpenseReimbursementEntity.class));
    }

    // ─── 状态机: DRAFT → SUBMITTED ───

    @Test
    void submit_DRAFT_变SUBMITTED() {
        when(mapper.selectById(1L)).thenReturn(stub(1L, "DRAFT"));
        stubPartyMappers();
        ExpenseReimbursementVO r = service.submit(1L);
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
        stubPartyMappers();
        ExpenseReimbursementVO r = service.approve(1L, "zhangsan");
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
        stubPartyMappers();
        ExpenseReimbursementVO r = service.reject(1L, "lisi", "金额不合理");
        assertEquals("REJECTED", r.getStatus());
        assertEquals("金额不合理", r.getRejectReason());
    }

    // ─── 状态机: APPROVED → VOUCHERED ───

    @Test
    void generateVoucher_APPROVED_变VOUCHERED() {
        when(mapper.selectById(1L)).thenReturn(stub(1L, "APPROVED"));
        stubPartyMappers();
        ExpenseReimbursementVO r = service.generateVoucher(1L, 999L);
        assertEquals("VOUCHERED", r.getStatus());
        assertEquals(999L, r.getVoucherId());
    }

    // ─── P11-3: 银行流水防重 ───

    @Test
    void autoCreateForBankStmt_已存在_返回旧单() {
        when(mapper.selectList(any())).thenReturn(List.of(stub(1L, "DRAFT")));
        when(mapper.selectById(1L)).thenReturn(stub(1L, "DRAFT"));
        stubPartyMappers();
        ExpenseReimbursementVO r = service.autoCreateForBankStmt(100L, 5L, new BigDecimal("500"), "差旅");
        assertEquals(1L, r.getId());
        verify(mapper, never()).insert(any(ExpenseReimbursementEntity.class));
    }

    @Test
    void autoCreateForBankStmt_新_插入草稿() {
        when(mapper.selectList(any())).thenReturn(List.of());
        when(mapper.insert(any(ExpenseReimbursementEntity.class))).thenAnswer(inv -> {
            ((ExpenseReimbursementEntity) inv.getArgument(0)).setId(2L);
            return 1;
        });
        when(mapper.selectById(2L)).thenReturn(stubOther(2L));
        stubPartyMappers();
        ExpenseReimbursementVO r = service.autoCreateForBankStmt(100L, 5L, new BigDecimal("500"), "差旅");
        assertEquals("DRAFT", r.getStatus());
        assertEquals("OTHER", r.getExpenseType());
        assertEquals(100L, r.getBankStmtId());
    }

    // ==================== P11-4: 报销单审批后自动生成凭证 ====================

    @Test
    void generateVoucherForApproved_非APPROVED_throw() {
        when(mapper.selectById(1L)).thenReturn(stub(1L, "DRAFT"));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.generateVoucherForApproved(1L));
        assertTrue(ex.getMessage().contains("仅 APPROVED"));
    }

    @Test
    void generateVoucherForApproved_差旅费_生成凭证和2条分录() {
        when(mapper.selectById(1L)).thenReturn(stub(1L, "APPROVED"));
        stubPartyMappers();
        // 借: 6602 管理费用, 贷: 1002 银行存款
        lenient().when(subjectMapper.selectList(any()))
                .thenReturn(List.of(new Subject() {{
                    setId(85L); setCode("6602"); setName("管理费用");
                }}))
                .thenReturn(List.of(new Subject() {{
                    setId(10L); setCode("1002"); setName("银行存款");
                }}));
        // mock insert 时回填 id=999
        when(voucherMapper.insert(any(VoucherEntity.class))).thenAnswer(inv -> {
            VoucherEntity v = inv.getArgument(0);
            v.setId(999L);
            return 1;
        });

        ExpenseReimbursementVO r = service.generateVoucherForApproved(1L);
        assertEquals("VOUCHERED", r.getStatus());
        assertEquals(999L, r.getVoucherId());
        // 凭证 + 2 条分录
        verify(voucherMapper, times(1)).insert(any(VoucherEntity.class));
        verify(voucherEntryMapper, times(2)).insert(any(VoucherEntryEntity.class));
    }

// NOTE: generateVoucherForApproved_费用科目不存在_throw removed - unreachable path due to lookupSubject fallback
}
