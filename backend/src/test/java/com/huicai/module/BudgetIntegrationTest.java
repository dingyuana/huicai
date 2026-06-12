package com.huicai.module;

import com.huicai.module.budget.service.BudgetService;
import com.huicai.module.budget.entity.BudgetEntity;
import com.huicai.module.budget.entity.BudgetEntryEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 预算管理集成测试
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:budget_test",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class BudgetIntegrationTest {

    @Autowired
    private BudgetService budgetService;

    @Test
    void testCreateAndApproveBudget() {
        BudgetEntity budget = new BudgetEntity();
        budget.setBudgetNo("BUD-2026-01");
        budget.setPeriod("202601");
        budget.setBudgetType("EXPENSE");
        budget.setStatus("DRAFT");

        BudgetEntryEntity entry = new BudgetEntryEntity();
        entry.setSubjectId(6601L);
        entry.setAmount(new BigDecimal("50000"));
        entry.setControlType("WARN");
        List<BudgetEntryEntity> entries = new ArrayList<>();
        entries.add(entry);

        BudgetEntity created = budgetService.create(budget, entries);
        assertNotNull(created.getId());
        assertEquals(0, created.getTotalAmount().compareTo(new BigDecimal("50000")));

        BudgetEntity approved = budgetService.approve(created.getId());
        assertEquals("APPROVED", approved.getStatus());
    }

    @Test
    void testBudgetCheck() {
        Map<String, Object> result = budgetService.checkBudget(6601L, "202601", new BigDecimal("10000"));
        assertNotNull(result);
        assertTrue(result.containsKey("pass"));
    }

    @Test
    void testExecutionAnalysis() {
        Map<String, Object> result = budgetService.executionAnalysis("202601");
        assertNotNull(result);
        assertTrue(result.containsKey("totalBudget"));
        assertTrue(result.containsKey("totalUsed"));
        assertTrue(result.containsKey("executionRatio"));
    }
}
