package com.huicai.base.report.service;

import com.huicai.base.report.entity.FinancialMetricEntity;
import com.huicai.base.report.mapper.FinancialMetricMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 财务分析服务
 * 计算各类财务指标: 盈利能力、偿债能力、运营能力、成长能力
 */
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final FinancialMetricMapper metricMapper;
    private final ReportService reportService;

    /**
     * 关键指标(盈利能力 + 偿债能力)
     */
    public Map<String, Object> keyMetrics(String period) {
        Map<String, Object> income = reportService.incomeStatement(period);
        Map<String, Object> balance = reportService.balanceSheet(period);

        BigDecimal revenue = toBd(income.get("revenue"));
        BigDecimal cost = toBd(income.get("cost"));
        BigDecimal profit = toBd(income.get("totalProfit"));
        BigDecimal totalAssets = toBd(balance.get("totalAssets"));
        BigDecimal totalLiab = toBd(balance.get("totalLiabilities"));
        BigDecimal totalEquity = toBd(balance.get("totalEquity"));

        // 模拟存货(简化: 实际应从库存模块取, 此处假设为流动资产的30%)
        BigDecimal currentAssets = toBd(balance.get("currentAssets"));
        BigDecimal inventory = currentAssets.multiply(new BigDecimal("0.3"));
        BigDecimal currentLiab = totalLiab.multiply(new BigDecimal("0.6"));

        Map<String, Object> result = new LinkedHashMap<>();
        // 盈利能力
        result.put("grossMargin", safeRatio(revenue.subtract(cost), revenue, 100));
        result.put("netMargin", safeRatio(profit, revenue, 100));
        result.put("roa", safeRatio(profit, totalAssets, 100));
        result.put("roe", safeRatio(profit, totalEquity, 100));
        // 偿债能力
        result.put("currentRatio", safeRatio(currentAssets, currentLiab, 1));
        result.put("quickRatio", safeRatio(currentAssets.subtract(inventory), currentLiab, 1));
        result.put("debtRatio", safeRatio(totalLiab, totalAssets, 100));
        return result;
    }

    /**
     * 杜邦分析
     * ROE = 净利率 × 资产周转率 × 权益乘数
     */
    public Map<String, Object> dupontAnalysis(String period) {
        Map<String, Object> metrics = keyMetrics(period);
        Map<String, Object> income = reportService.incomeStatement(period);
        Map<String, Object> balance = reportService.balanceSheet(period);

        BigDecimal profit = toBd(income.get("totalProfit"));
        BigDecimal revenue = toBd(income.get("revenue"));
        BigDecimal totalAssets = toBd(balance.get("totalAssets"));
        BigDecimal totalEquity = toBd(balance.get("totalEquity"));

        BigDecimal netMargin = safeRatio(profit, revenue, 1);
        BigDecimal assetTurnover = safeRatio(revenue, totalAssets, 1);
        BigDecimal equityMultiplier = safeRatio(totalAssets, totalEquity, 1);
        BigDecimal roe = netMargin.multiply(assetTurnover).multiply(equityMultiplier)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("netMargin", netMargin.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));
        result.put("assetTurnover", assetTurnover.setScale(2, RoundingMode.HALF_UP));
        result.put("equityMultiplier", equityMultiplier.setScale(2, RoundingMode.HALF_UP));
        result.put("roe", roe);
        return result;
    }

    /**
     * 同比环比(简化: 与上一期比较)
     */
    public Map<String, Object> yoyMom(String period) {
        String year = period.substring(0, 4);
        int month = Integer.parseInt(period.substring(4, 6));
        String prevPeriod;
        if (month == 1) {
            int prevYear = Integer.parseInt(year) - 1;
            prevPeriod = prevYear + "12";
        } else {
            prevPeriod = year + String.format("%02d", month - 1);
        }
        String prevYearPeriod = (Integer.parseInt(year) - 1) + period.substring(4);

        Map<String, Object> current = reportService.incomeStatement(period);
        Map<String, Object> prev = reportService.incomeStatement(prevPeriod);
        Map<String, Object> prevYear = reportService.incomeStatement(prevYearPeriod);

        BigDecimal curRev = toBd(current.get("revenue"));
        BigDecimal prevRev = toBd(prev.get("revenue"));
        BigDecimal prevYearRev = toBd(prevYear.get("revenue"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentRevenue", curRev);
        result.put("momRatio", safeRatio(curRev.subtract(prevRev), prevRev, 100));
        result.put("yoyRatio", safeRatio(curRev.subtract(prevYearRev), prevYearRev, 100));
        return result;
    }

    /**
     * 全部指标定义
     */
    public List<FinancialMetricEntity> listMetrics() {
        return metricMapper.selectList(null);
    }

    private BigDecimal safeRatio(BigDecimal numerator, BigDecimal denominator, int scaleFactor) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.multiply(BigDecimal.valueOf(scaleFactor))
                .divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal toBd(Object o) {
        if (o == null) return BigDecimal.ZERO;
        return new BigDecimal(o.toString());
    }
}
