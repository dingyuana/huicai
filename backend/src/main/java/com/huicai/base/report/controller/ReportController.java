package com.huicai.base.report.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.huicai.base.report.service.AnalysisService;
import com.huicai.base.report.service.ReportService;
import com.huicai.common.response.R;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Tag(name = "报表中心")
@RestController
@RequestMapping("/api/base/report/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final AnalysisService analysisService;

    @Operation(summary = "科目余额表")
    @GetMapping("/subject-balance")
    public R<List<Map<String, Object>>> subjectBalance(@RequestParam String period) {
        return R.ok(reportService.subjectBalanceTable(period));
    }

    @Operation(summary = "资产负债表")
    @GetMapping("/balance-sheet")
    public R<Map<String, Object>> balanceSheet(@RequestParam String period) {
        return R.ok(reportService.balanceSheet(period));
    }

    @Operation(summary = "利润表")
    @GetMapping("/income-statement")
    public R<Map<String, Object>> incomeStatement(@RequestParam String period) {
        return R.ok(reportService.incomeStatement(period));
    }

    @Operation(summary = "现金流量表")
    @GetMapping("/cash-flow")
    public R<Map<String, Object>> cashFlow(@RequestParam String period) {
        return R.ok(reportService.cashFlowStatement(period));
    }

    @Operation(summary = "趋势数据(多期)")
    @GetMapping("/trend")
    public R<List<Map<String, Object>>> trend(
            @RequestParam String startPeriod,
            @RequestParam String endPeriod) {
        return R.ok(reportService.trend(startPeriod, endPeriod));
    }

    @Operation(summary = "关键指标")
    @GetMapping("/analysis/key-metrics")
    public R<Map<String, Object>> keyMetrics(@RequestParam String period) {
        return R.ok(analysisService.keyMetrics(period));
    }

    @Operation(summary = "杜邦分析")
    @GetMapping("/analysis/dupont")
    public R<Map<String, Object>> dupont(@RequestParam String period) {
        return R.ok(analysisService.dupontAnalysis(period));
    }

    @Operation(summary = "同比环比")
    @GetMapping("/analysis/yoy-mom")
    public R<Map<String, Object>> yoyMom(@RequestParam String period) {
        return R.ok(analysisService.yoyMom(period));
    }

    @Operation(summary = "科目余额表导出")
    @GetMapping("/subject-balance/export")
    public void exportSubjectBalance(@RequestParam String period, HttpServletResponse response) throws IOException {
        reportService.exportSubjectBalance(period, response);
    }

    @Operation(summary = "资产负债表导出")
    @GetMapping("/balance-sheet/export")
    public void exportBalanceSheet(@RequestParam String period, HttpServletResponse response) throws IOException {
        reportService.exportBalanceSheet(period, response);
    }

    @Operation(summary = "利润表导出")
    @GetMapping("/income-statement/export")
    public void exportIncomeStatement(@RequestParam String period, HttpServletResponse response) throws IOException {
        reportService.exportIncomeStatement(period, response);
    }

    @Operation(summary = "现金流量表导出")
    @GetMapping("/cash-flow/export")
    public void exportCashFlow(@RequestParam String period, HttpServletResponse response) throws IOException {
        reportService.exportCashFlow(period, response);
    }

    @Operation(summary = "指标定义")
    @GetMapping("/analysis/metrics")
    public R<List<Map<String, Object>>> metrics() {
        List<Map<String, Object>> list = analysisService.listMetrics().stream()
                .map(m -> {
                    Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("code", m.getMetricCode());
                    map.put("name", m.getMetricName());
                    map.put("category", m.getCategory());
                    map.put("unit", m.getUnit());
                    map.put("description", m.getDescription());
                    map.put("formula", m.getFormula());
                    return map;
                }).toList();
        return R.ok(list);
    }
}
