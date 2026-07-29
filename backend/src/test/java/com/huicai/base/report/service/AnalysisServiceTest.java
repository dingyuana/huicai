package com.huicai.base.report.service;

import com.huicai.base.report.entity.FinancialMetricEntity;
import com.huicai.base.report.mapper.FinancialMetricMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalysisService 财务分析服务")
class AnalysisServiceTest {

    @Mock
    private FinancialMetricMapper metricMapper;

    @Mock
    private ReportService reportService;

    private AnalysisService createService() {
        return new AnalysisService(metricMapper, reportService);
    }

    @Test
    @DisplayName("listMetrics: 返回全部指标列表")
    void listMetrics_returnsAllMetrics() {
        List<FinancialMetricEntity> mockList = new ArrayList<>();
        FinancialMetricEntity e1 = new FinancialMetricEntity();
        e1.setMetricCode("grossMargin");
        e1.setMetricName("毛利率");
        e1.setCategory("profitability");
        mockList.add(e1);
        FinancialMetricEntity e2 = new FinancialMetricEntity();
        e2.setMetricCode("roe");
        e2.setMetricName("净资产收益率");
        e2.setCategory("profitability");
        mockList.add(e2);
        when(metricMapper.selectList(null)).thenReturn(mockList);

        AnalysisService service = createService();
        List<FinancialMetricEntity> result = service.listMetrics();

        assertEquals(2, result.size());
        assertEquals("grossMargin", result.get(0).getMetricCode());
        assertEquals("roe", result.get(1).getMetricCode());
        verify(metricMapper).selectList(null);
    }

    @Test
    @DisplayName("keyMetrics: 正常计算关键指标（毛利率、净利率、ROA、ROE、流动比率、速动比率、资产负债率）")
    void keyMetrics_calculatesAllRatios() {
        Map<String, Object> income = new HashMap<>();
        income.put("revenue", 100000);
        income.put("cost", 60000);
        income.put("totalProfit", 20000);
        when(reportService.incomeStatement("202606")).thenReturn(income);

        Map<String, Object> balance = new HashMap<>();
        balance.put("totalAssets", 500000);
        balance.put("totalLiabilities", 300000);
        balance.put("totalEquity", 200000);
        balance.put("currentAssets", 200000);
        when(reportService.balanceSheet("202606")).thenReturn(balance);

        AnalysisService service = createService();
        Map<String, Object> result = service.keyMetrics("202606");

        // 盈利能力
        assertEquals(new BigDecimal("40.00"), result.get("grossMargin"), "毛利率应为 40%");
        assertEquals(new BigDecimal("20.00"), result.get("netMargin"), "净利率应为 20%");
        assertEquals(new BigDecimal("4.00"), result.get("roa"), "ROA 应为 4%");
        assertEquals(new BigDecimal("10.00"), result.get("roe"), "ROE 应为 10%");

        // 偿债能力: currentAssets=200000, inventory=60000, currentLiab=180000
        // currentRatio = 200000/180000 = 1.11
        assertEquals(new BigDecimal("1.11"), result.get("currentRatio"), "流动比率应为 1.11");
        // quickRatio = (200000-60000)/180000 = 140000/180000 = 0.78
        assertEquals(new BigDecimal("0.78"), result.get("quickRatio"), "速动比率应为 0.78");
        // debtRatio = 300000/500000*100 = 60.00
        assertEquals(new BigDecimal("60.00"), result.get("debtRatio"), "资产负债率应为 60%");

        verify(reportService).incomeStatement("202606");
        verify(reportService).balanceSheet("202606");
    }

    @Test
    @DisplayName("keyMetrics: 分母为零时返回 0（安全除零）")
    void keyMetrics_zeroDenominator_returnsZero() {
        Map<String, Object> income = new HashMap<>();
        income.put("revenue", BigDecimal.ZERO);
        income.put("cost", BigDecimal.ZERO);
        income.put("totalProfit", BigDecimal.ZERO);
        when(reportService.incomeStatement("202606")).thenReturn(income);

        Map<String, Object> balance = new HashMap<>();
        balance.put("totalAssets", BigDecimal.ZERO);
        balance.put("totalLiabilities", BigDecimal.ZERO);
        balance.put("totalEquity", BigDecimal.ZERO);
        balance.put("currentAssets", BigDecimal.ZERO);
        when(reportService.balanceSheet("202606")).thenReturn(balance);

        AnalysisService service = createService();
        Map<String, Object> result = service.keyMetrics("202606");

        assertEquals(BigDecimal.ZERO, result.get("grossMargin"));
        assertEquals(BigDecimal.ZERO, result.get("netMargin"));
        assertEquals(BigDecimal.ZERO, result.get("roa"));
        assertEquals(BigDecimal.ZERO, result.get("roe"));
        assertEquals(BigDecimal.ZERO, result.get("currentRatio"));
        assertEquals(BigDecimal.ZERO, result.get("quickRatio"));
        assertEquals(BigDecimal.ZERO, result.get("debtRatio"));
    }

    @Test
    @DisplayName("keyMetrics: 部分字段为 null 时安全处理")
    void keyMetrics_nullFields_returnsZero() {
        when(reportService.incomeStatement("202606")).thenReturn(new HashMap<>());
        when(reportService.balanceSheet("202606")).thenReturn(new HashMap<>());

        AnalysisService service = createService();
        Map<String, Object> result = service.keyMetrics("202606");

        // toBd(null) 返回 BigDecimal.ZERO，所有分母为 0，safeRatio 返回 0
        assertEquals(BigDecimal.ZERO, result.get("grossMargin"));
        assertEquals(BigDecimal.ZERO, result.get("netMargin"));
        assertEquals(BigDecimal.ZERO, result.get("roa"));
        assertEquals(BigDecimal.ZERO, result.get("roe"));
        assertEquals(BigDecimal.ZERO, result.get("currentRatio"));
        assertEquals(BigDecimal.ZERO, result.get("quickRatio"));
        assertEquals(BigDecimal.ZERO, result.get("debtRatio"));
    }

    @Test
    @DisplayName("dupontAnalysis: 杜邦分析 ROE 分解（净利率 × 资产周转率 × 权益乘数）")
    void dupontAnalysis_decomposesROE() {
        Map<String, Object> income = new HashMap<>();
        income.put("revenue", 100000);
        income.put("cost", 60000);
        income.put("totalProfit", 20000);
        when(reportService.incomeStatement("202606")).thenReturn(income);

        Map<String, Object> balance = new HashMap<>();
        balance.put("totalAssets", 500000);
        balance.put("totalLiabilities", 300000);
        balance.put("totalEquity", 200000);
        balance.put("currentAssets", 200000);
        when(reportService.balanceSheet("202606")).thenReturn(balance);

        AnalysisService service = createService();
        Map<String, Object> result = service.dupontAnalysis("202606");

        // netMargin = 20000/100000 * 100 = 20.00
        assertEquals(new BigDecimal("20.00"), result.get("netMargin"));
        // assetTurnover = 100000/500000 = 0.20
        assertEquals(new BigDecimal("0.20"), result.get("assetTurnover"));
        // equityMultiplier = 500000/200000 = 2.50
        assertEquals(new BigDecimal("2.50"), result.get("equityMultiplier"));
        // roe = 0.20 * 0.20 * 2.50 * 100 = 10.00
        assertEquals(new BigDecimal("10.00"), result.get("roe"));

        verify(reportService, times(2)).incomeStatement("202606");
        verify(reportService, times(2)).balanceSheet("202606");
    }

    @Test
    @DisplayName("yoyMom: 同比环比计算（本月和上月）")
    void yoyMom_calculatesMomAndYoy() {
        Map<String, Object> current = new HashMap<>();
        current.put("revenue", 100000);
        when(reportService.incomeStatement("202606")).thenReturn(current);

        Map<String, Object> prev = new HashMap<>();
        prev.put("revenue", 80000);
        when(reportService.incomeStatement("202605")).thenReturn(prev);

        Map<String, Object> prevYear = new HashMap<>();
        prevYear.put("revenue", 70000);
        when(reportService.incomeStatement("202506")).thenReturn(prevYear);

        AnalysisService service = createService();
        Map<String, Object> result = service.yoyMom("202606");

        assertEquals(new BigDecimal("100000"), result.get("currentRevenue"));
        // momRatio = (100000-80000)/80000 * 100 = 25.00
        assertEquals(new BigDecimal("25.00"), result.get("momRatio"));
        // yoyRatio = (100000-70000)/70000 * 100 ≈ 42.86
        assertEquals(new BigDecimal("42.86"), result.get("yoyRatio"));
    }

    @Test
    @DisplayName("yoyMom: 1月份跨年计算上月为去年12月")
    void yoyMom_january_crossYearPrevMonth() {
        Map<String, Object> current = new HashMap<>();
        current.put("revenue", 50000);
        when(reportService.incomeStatement("202601")).thenReturn(current);

        // 1月份时上月应为去年12月
        Map<String, Object> prev = new HashMap<>();
        prev.put("revenue", 60000);
        when(reportService.incomeStatement("202512")).thenReturn(prev);

        // 去年同期为去年1月
        Map<String, Object> prevYear = new HashMap<>();
        prevYear.put("revenue", 40000);
        when(reportService.incomeStatement("202501")).thenReturn(prevYear);

        AnalysisService service = createService();
        Map<String, Object> result = service.yoyMom("202601");

        assertEquals(new BigDecimal("50000"), result.get("currentRevenue"));
        // momRatio = (50000-60000)/60000 * 100 = -16.67
        assertEquals(new BigDecimal("-16.67"), result.get("momRatio"));
        // yoyRatio = (50000-40000)/40000 * 100 = 25.00
        assertEquals(new BigDecimal("25.00"), result.get("yoyRatio"));
    }

    @Test
    @DisplayName("yoyMom: 上月收入为零时环比返回 0")
    void yoyMom_prevMonthZeroRevenue_returnsZero() {
        Map<String, Object> current = new HashMap<>();
        current.put("revenue", 50000);
        when(reportService.incomeStatement("202606")).thenReturn(current);

        Map<String, Object> prev = new HashMap<>();
        prev.put("revenue", BigDecimal.ZERO);
        when(reportService.incomeStatement("202605")).thenReturn(prev);

        Map<String, Object> prevYear = new HashMap<>();
        prevYear.put("revenue", 40000);
        when(reportService.incomeStatement("202506")).thenReturn(prevYear);

        AnalysisService service = createService();
        Map<String, Object> result = service.yoyMom("202606");

        assertEquals(new BigDecimal("50000"), result.get("currentRevenue"));
        // 上月收入为0，分母为0，返回0
        assertEquals(BigDecimal.ZERO, result.get("momRatio"));
        // yoyRatio = (50000-40000)/40000 * 100 = 25.00
        assertEquals(new BigDecimal("25.00"), result.get("yoyRatio"));
    }
}