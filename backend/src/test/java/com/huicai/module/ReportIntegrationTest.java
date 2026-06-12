package com.huicai.module;

import com.huicai.module.report.service.AnalysisService;
import com.huicai.module.report.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 报表与分析集成测试
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:report_test",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class ReportIntegrationTest {

    @Autowired
    private ReportService reportService;

    @Autowired
    private AnalysisService analysisService;

    @Test
    void testSubjectBalance() {
        List<Map<String, Object>> list = reportService.subjectBalanceTable("202601");
        assertNotNull(list);
    }

    @Test
    void testBalanceSheet() {
        Map<String, Object> result = reportService.balanceSheet("202601");
        assertNotNull(result);
        assertTrue(result.containsKey("assets"));
        assertTrue(result.containsKey("liabilities"));
        assertTrue(result.containsKey("equity"));
        assertTrue(result.containsKey("balanced"));
    }

    @Test
    void testIncomeStatement() {
        Map<String, Object> result = reportService.incomeStatement("202601");
        assertNotNull(result);
        assertTrue(result.containsKey("revenue"));
        assertTrue(result.containsKey("cost"));
        assertTrue(result.containsKey("grossProfit"));
        assertTrue(result.containsKey("totalProfit"));
    }

    @Test
    void testCashFlowStatement() {
        Map<String, Object> result = reportService.cashFlowStatement("202601");
        assertNotNull(result);
        assertTrue(result.containsKey("operatingNet"));
        assertTrue(result.containsKey("investingNet"));
        assertTrue(result.containsKey("financingNet"));
        assertTrue(result.containsKey("totalNet"));
    }

    @Test
    void testKeyMetrics() {
        Map<String, Object> result = analysisService.keyMetrics("202601");
        assertNotNull(result);
        assertTrue(result.containsKey("grossMargin"));
        assertTrue(result.containsKey("netMargin"));
        assertTrue(result.containsKey("roe"));
        assertTrue(result.containsKey("debtRatio"));
    }

    @Test
    void testDupontAnalysis() {
        Map<String, Object> result = analysisService.dupontAnalysis("202601");
        assertNotNull(result);
        assertTrue(result.containsKey("netMargin"));
        assertTrue(result.containsKey("assetTurnover"));
        assertTrue(result.containsKey("equityMultiplier"));
        assertTrue(result.containsKey("roe"));
    }
}
