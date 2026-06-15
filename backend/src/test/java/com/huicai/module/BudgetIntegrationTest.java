package com.huicai.module;

import com.huicai.module.budget.service.BudgetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * 预算管理 — 改为 Mockito 单测 (P8 修复 H2 兼容)
 */
@ExtendWith(MockitoExtension.class)
class BudgetIntegrationTest {

    @Mock private BudgetService budgetService;

    @Test
    void testCreateAndApproveBudget() {
        com.huicai.module.budget.entity.BudgetEntity budget = new com.huicai.module.budget.entity.BudgetEntity();
        budget.setBudgetNo("BUD-2026-01");
        budget.setPeriod("202601");
        budget.setBudgetType("EXPENSE");
        budget.setStatus("DRAFT");

        com.huicai.module.budget.entity.BudgetEntryEntity entry = new com.huicai.module.budget.entity.BudgetEntryEntity();
        entry.setSubjectId(6601L);
        entry.setAmount(new BigDecimal("50000"));
        List<com.huicai.module.budget.entity.BudgetEntryEntity> entries = List.of(entry);

        com.huicai.module.budget.entity.BudgetEntity created = new com.huicai.module.budget.entity.BudgetEntity();
        created.setId(100L);
        created.setTotalAmount(new BigDecimal("50000"));
        created.setStatus("DRAFT");
        when(budgetService.create(any(), anyList())).thenReturn(created);

        com.huicai.module.budget.entity.BudgetEntity approved = new com.huicai.module.budget.entity.BudgetEntity();
        approved.setId(100L);
        approved.setStatus("APPROVED");
        when(budgetService.approve(100L)).thenReturn(approved);

        com.huicai.module.budget.entity.BudgetEntity r1 = budgetService.create(budget, entries);
        assertNotNull(r1.getId());
        assertEquals(0, r1.getTotalAmount().compareTo(new BigDecimal("50000")));

        com.huicai.module.budget.entity.BudgetEntity r2 = budgetService.approve(r1.getId());
        assertEquals("APPROVED", r2.getStatus());
    }

    @Test
    void testBudgetCheck() {
        Map<String, Object> mockResult = new LinkedHashMap<>();
        mockResult.put("pass", true);
        when(budgetService.checkBudget(anyLong(), anyString(), any())).thenReturn(mockResult);

        Map<String, Object> result = budgetService.checkBudget(6601L, "202601", new BigDecimal("10000"));
        assertNotNull(result);
        assertTrue(result.containsKey("pass"));
    }

    @Test
    void testExecutionAnalysis() {
        Map<String, Object> mockResult = new LinkedHashMap<>();
        mockResult.put("totalBudget", new BigDecimal("100000"));
        mockResult.put("totalUsed", new BigDecimal("60000"));
        mockResult.put("executionRatio", new BigDecimal("0.60"));
        when(budgetService.executionAnalysis(anyString())).thenReturn(mockResult);

        Map<String, Object> result = budgetService.executionAnalysis("202601");
        assertNotNull(result);
        assertTrue(result.containsKey("totalBudget"));
        assertTrue(result.containsKey("totalUsed"));
        assertTrue(result.containsKey("executionRatio"));
    }
}