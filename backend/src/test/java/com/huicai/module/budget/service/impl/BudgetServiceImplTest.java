package com.huicai.module.budget.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.budget.entity.BudgetEntity;
import com.huicai.module.budget.mapper.BudgetAdjustmentMapper;
import com.huicai.module.budget.mapper.BudgetEntryMapper;
import com.huicai.module.budget.mapper.BudgetMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetServiceImplTest {

    @Mock private BudgetMapper budgetMapper;
    @Mock private BudgetEntryMapper entryMapper;
    @Mock private BudgetAdjustmentMapper adjustmentMapper;
    @InjectMocks private BudgetServiceImpl service;

    private BudgetEntity stubBudget(Long id, String status) {
        BudgetEntity b = new BudgetEntity();
        b.setId(id);
        b.setBudgetNo("BUD-202606-001");
        b.setPeriod("202606");
        b.setTotalAmount(new BigDecimal("10000.00"));
        b.setStatus(status);
        return b;
    }

    @Test
    void approve_draft_becomes_approved() {
        when(budgetMapper.selectById(1L)).thenReturn(stubBudget(1L, "DRAFT"));
        BudgetEntity r = service.approve(1L);
        assertEquals("APPROVED", r.getStatus());
        assertNotNull(r.getApprovedAt());
        verify(budgetMapper).updateById(any(BudgetEntity.class));
    }

    @Test
    void approve_non_draft_throws() {
        when(budgetMapper.selectById(1L)).thenReturn(stubBudget(1L, "APPROVED"));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(1L));
        assertTrue(ex.getMessage().contains("仅草稿状态可审批"));
    }

    @Test
    void checkBudget_under_budget_passes() {
        Map<String, Object> entry = new HashMap<>();
        entry.put("amount", new BigDecimal("1000.00"));
        entry.put("usedAmount", new BigDecimal("200.00"));
        entry.put("controlType", "WARN");
        when(entryMapper.findBySubjectAndPeriod(10L, "202606")).thenReturn(List.of(entry));

        Map<String, Object> r = service.checkBudget(10L, "202606", new BigDecimal("500.00"));
        assertTrue((Boolean) r.get("pass"));
        assertEquals(new BigDecimal("1000.00"), r.get("budget"));
        assertEquals(new BigDecimal("700.00"), r.get("newUsed"));
    }

    @Test
    void checkBudget_over_budget_approve_mode() {
        Map<String, Object> entry = new HashMap<>();
        entry.put("amount", new BigDecimal("1000.00"));
        entry.put("usedAmount", new BigDecimal("800.00"));
        entry.put("controlType", "APPROVE");
        when(entryMapper.findBySubjectAndPeriod(10L, "202606")).thenReturn(List.of(entry));

        // 用 500, 超预算 1300 vs 1000
        Map<String, Object> r = service.checkBudget(10L, "202606", new BigDecimal("500.00"));
        assertTrue((Boolean) r.get("pass"));
        assertEquals("REQUIRE_APPROVE", r.get("action"));
    }

    @Test
    void executionAnalysis_with_data_returns_summary() {
        BudgetEntity b = stubBudget(1L, "ACTIVE");
        when(budgetMapper.selectList(any())).thenReturn(List.of(b));
        // entry 累计 usedAmount
        com.huicai.module.budget.entity.BudgetEntryEntity e =
                new com.huicai.module.budget.entity.BudgetEntryEntity();
        e.setUsedAmount(new BigDecimal("3000.00"));
        when(entryMapper.selectList(any())).thenReturn(List.of(e));

        Map<String, Object> r = service.executionAnalysis("202606");
        assertEquals("202606", r.get("period"));
        assertEquals(new BigDecimal("10000.00"), r.get("totalBudget"));
        assertEquals(new BigDecimal("3000.00"), r.get("totalUsed"));
        assertEquals(new BigDecimal("7000.00"), r.get("remaining"));
    }
}
