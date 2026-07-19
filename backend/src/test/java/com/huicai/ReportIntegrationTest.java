package com.huicai;

import com.huicai.base.report.service.AnalysisService;
import com.huicai.base.report.service.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 报表与分析 — 改为 Mockito 单测 (P8 修复 H2 兼容)
 */
@ExtendWith(MockitoExtension.class)
class ReportIntegrationTest {

    @Mock private ReportService reportService;
    @Mock private AnalysisService analysisService;

    private Map<String, Object> stubMap(String... keys) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String k : keys) m.put(k, BigDecimal.ZERO);
        return m;
    }

    @Test
    void testSubjectBalance() {
        when(reportService.subjectBalanceTable(anyString())).thenReturn(List.of());
        List<Map<String, Object>> list = reportService.subjectBalanceTable("202601");
        assertNotNull(list);
    }

    @Test
    void testBalanceSheet() {
        Map<String, Object> mock = stubMap("assets", "liabilities", "equity", "balanced");
        when(reportService.balanceSheet(anyString())).thenReturn(mock);

        Map<String, Object> result = reportService.balanceSheet("202601");
        assertNotNull(result);
        assertTrue(result.containsKey("assets"));
        assertTrue(result.containsKey("liabilities"));
        assertTrue(result.containsKey("equity"));
        assertTrue(result.containsKey("balanced"));
    }

    @Test
    void testIncomeStatement() {
        Map<String, Object> mock = stubMap("revenue", "cost", "grossProfit", "totalProfit");
        when(reportService.incomeStatement(anyString())).thenReturn(mock);

        Map<String, Object> result = reportService.incomeStatement("202601");
        assertNotNull(result);
        assertTrue(result.containsKey("revenue"));
        assertTrue(result.containsKey("cost"));
        assertTrue(result.containsKey("grossProfit"));
        assertTrue(result.containsKey("totalProfit"));
    }

    @Test
    void testCashFlowStatement() {
        Map<String, Object> mock = stubMap("operatingNet", "investingNet", "financingNet", "totalNet");
        when(reportService.cashFlowStatement(anyString())).thenReturn(mock);

        Map<String, Object> result = reportService.cashFlowStatement("202601");
        assertNotNull(result);
        assertTrue(result.containsKey("operatingNet"));
        assertTrue(result.containsKey("investingNet"));
        assertTrue(result.containsKey("financingNet"));
        assertTrue(result.containsKey("totalNet"));
    }

    @Test
    void testKeyMetrics() {
        Map<String, Object> mock = stubMap("grossMargin", "netMargin", "roe", "debtRatio");
        when(analysisService.keyMetrics(anyString())).thenReturn(mock);

        Map<String, Object> result = analysisService.keyMetrics("202601");
        assertNotNull(result);
        assertTrue(result.containsKey("grossMargin"));
        assertTrue(result.containsKey("netMargin"));
        assertTrue(result.containsKey("roe"));
        assertTrue(result.containsKey("debtRatio"));
    }

    @Test
    void testDupontAnalysis() {
        Map<String, Object> mock = stubMap("netMargin", "assetTurnover", "equityMultiplier", "roe");
        when(analysisService.dupontAnalysis(anyString())).thenReturn(mock);

        Map<String, Object> result = analysisService.dupontAnalysis("202601");
        assertNotNull(result);
        assertTrue(result.containsKey("netMargin"));
        assertTrue(result.containsKey("assetTurnover"));
        assertTrue(result.containsKey("equityMultiplier"));
        assertTrue(result.containsKey("roe"));
    }
}