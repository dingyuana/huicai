package com.huicai.module.arap.controller;

import com.huicai.common.response.R;
import com.huicai.module.arap.service.AgingAnalysisService;
import com.huicai.module.arap.service.AgingAnalysisService.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "账龄分析")
@RestController
@RequestMapping("/api/v1/aging-analysis")
@RequiredArgsConstructor
public class AgingAnalysisController {

    private final AgingAnalysisService service;

    @Operation(summary = "账龄分析汇总（按区间分布）")
    @GetMapping("/summary")
    public R<AgingSummaryVO> getAgingSummary(
            @RequestParam String period,
            @RequestParam(required = false) Long customerId) {
        return R.ok(service.getAgingSummary(period, customerId));
    }

    @Operation(summary = "按客户维度的账龄分析")
    @GetMapping("/by-customer")
    public R<List<AgingByCustomerVO>> getAgingByCustomer(@RequestParam String period) {
        return R.ok(service.getAgingByCustomer(period));
    }

    @Operation(summary = "到期债权表（已到期未核销明细）")
    @GetMapping("/due-receivables")
    public R<DueReceivablesVO> getDueReceivables(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long customerId) {
        return R.ok(service.getDueReceivables(date, customerId));
    }

    @Operation(summary = "查询逾期预警列表")
    @GetMapping("/alerts")
    public R<List<AgingAlertVO>> getAlerts(
            @RequestParam(required = false) String alertLevel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long customerId) {
        return R.ok(service.getAlerts(alertLevel, status, customerId));
    }

    @Operation(summary = "手动触发逾期预警扫描")
    @PostMapping("/alerts/generate")
    public R<Integer> generateAlerts(@RequestParam String period) {
        return R.ok(service.generateAlerts(period));
    }

    @Operation(summary = "忽略指定预警")
    @PostMapping("/alerts/{id}/dismiss")
    public R<Void> dismissAlert(@PathVariable Long id) {
        service.dismissAlert(id);
        return R.ok();
    }

    @Operation(summary = "标记预警已解决")
    @PostMapping("/alerts/{id}/resolve")
    public R<Void> resolveAlert(@PathVariable Long id) {
        service.resolveAlert(id);
        return R.ok();
    }

    @Operation(summary = "获取完整账龄分析报告")
    @GetMapping("/report")
    public R<AgingSummaryVO> getReport(@RequestParam String period) {
        return R.ok(service.getAgingSummary(period, null));
    }
}