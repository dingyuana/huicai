package com.huicai.base.system.controller;

import com.huicai.base.system.service.SystemClearService;
import com.huicai.common.response.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据维护（清库）控制器。
 *
 * <p>🔴 <b>P148 / REQ-2026-164 加固说明</b>：本控制器原先直接执行无 WHERE 的全表 DELETE，
 * 一次调用可跨所有账套清空数据（2026-10-30 实测清掉企业 4 的 26 张凭证并留下 26 条悬空引用）。
 * 现改为**薄委托层**，所有清库逻辑收口到 {@link SystemClearService}，并强制两道护栏：</p>
 * <ol>
 *   <li><b>确认令牌</b>：必须传 `confirm=&lt;动作名&gt;`（如 `CLEAR_VOUCHERS`），否则 400；</li>
 *   <li><b>按企业隔离</b>：必须能确定目标企业（上下文或显式 `enterpriseId`），
 *       且每条 SQL 都带 `enterprise_id = ?`；无法确定企业时 400 拒绝，**不退化为清全部**。</li>
 * </ol>
 *
 * <p>两个校验都在 service 内、**任何 DELETE 之前**执行，故“没确认就一行都不动”。</p>
 */
@Slf4j
@Tag(name = "数据维护")
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemClearController {

    private final SystemClearService systemClearService;
    private final JdbcTemplate jdbcTemplate;

    private Map<String, Object> result(String message) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("message", message);
        return r;
    }

    @Operation(summary = "清空银行流水（需 confirm=CLEAR_BANK_STATEMENTS，按企业隔离）")
    @PostMapping("/clear-bank-statements")
    @PreAuthorize("hasAuthority('system:clear')")
    public R<Map<String, Object>> clearBankStatements(
            @RequestParam(value = "confirm", required = false) String confirm,
            @RequestParam(value = "enterpriseId", required = false) Long enterpriseId) {
        return R.ok(result(systemClearService.clearBankStatements(confirm, enterpriseId)));
    }

    @Operation(summary = "清空发票记录（需 confirm=CLEAR_INVOICE_RECORDS，按企业隔离）")
    @PostMapping("/clear-invoice-records")
    @PreAuthorize("hasAuthority('system:clear')")
    public R<Map<String, Object>> clearInvoiceRecords(
            @RequestParam(value = "confirm", required = false) String confirm,
            @RequestParam(value = "enterpriseId", required = false) Long enterpriseId) {
        return R.ok(result(systemClearService.clearInvoiceRecords(confirm, enterpriseId)));
    }

    @Operation(summary = "清空所有凭证（需 confirm=CLEAR_VOUCHERS，按企业隔离）")
    @PostMapping("/clear-vouchers")
    @PreAuthorize("hasAuthority('system:clear')")
    public R<Map<String, Object>> clearVouchers(
            @RequestParam(value = "confirm", required = false) String confirm,
            @RequestParam(value = "enterpriseId", required = false) Long enterpriseId) {
        return R.ok(result(systemClearService.clearVouchers(confirm, enterpriseId)));
    }

    @Operation(summary = "清空报表数据（需 confirm=CLEAR_REPORT_DATA，按企业隔离）")
    @PostMapping("/clear-report-data")
    @PreAuthorize("hasAuthority('system:clear')")
    public R<Map<String, Object>> clearReportData(
            @RequestParam(value = "confirm", required = false) String confirm,
            @RequestParam(value = "enterpriseId", required = false) Long enterpriseId) {
        return R.ok(result(systemClearService.clearReportData(confirm, enterpriseId)));
    }

    @Operation(summary = "报表数据统计（只读）")
    @GetMapping("/report-data-stats")
    public R<Long> reportDataStats() {
        Long balance = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_subject_balance", Long.class);
        Long cashFlow = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_voucher_cash_flow", Long.class);
        Long total = (balance == null ? 0L : balance) + (cashFlow == null ? 0L : cashFlow);
        log.info("报表数据统计: subject_balance={}, cash_flow={}, total={}", balance, cashFlow, total);
        return R.ok(total);
    }

    @Operation(summary = "清空业务单据（需 confirm=CLEAR_BUSINESS_DOCS，按企业隔离）")
    @PostMapping("/clear-business-docs")
    @PreAuthorize("hasAuthority('system:clear')")
    public R<Map<String, Object>> clearBusinessDocs(
            @RequestParam(value = "confirm", required = false) String confirm,
            @RequestParam(value = "enterpriseId", required = false) Long enterpriseId) {
        return R.ok(result(systemClearService.clearBusinessDocs(confirm, enterpriseId)));
    }

    @Operation(summary = "清空应收相关数据（需 confirm=CLEAR_RECEIVABLES，按企业隔离）")
    @PostMapping("/clear-receivables")
    @PreAuthorize("hasAuthority('system:clear')")
    public R<Map<String, Object>> clearReceivables(
            @RequestParam(value = "confirm", required = false) String confirm,
            @RequestParam(value = "enterpriseId", required = false) Long enterpriseId) {
        return R.ok(result(systemClearService.clearReceivables(confirm, enterpriseId)));
    }

    @Operation(summary = "清空应付相关数据（需 confirm=CLEAR_PAYABLES，按企业隔离）")
    @PostMapping("/clear-payables")
    @PreAuthorize("hasAuthority('system:clear')")
    public R<Map<String, Object>> clearPayables(
            @RequestParam(value = "confirm", required = false) String confirm,
            @RequestParam(value = "enterpriseId", required = false) Long enterpriseId) {
        return R.ok(result(systemClearService.clearPayables(confirm, enterpriseId)));
    }

    @Operation(summary = "清空核销数据（需 confirm=CLEAR_SETTLEMENTS，按企业隔离）")
    @PostMapping("/clear-settlements")
    @PreAuthorize("hasAuthority('system:clear')")
    public R<Map<String, Object>> clearSettlements(
            @RequestParam(value = "confirm", required = false) String confirm,
            @RequestParam(value = "enterpriseId", required = false) Long enterpriseId) {
        return R.ok(result(systemClearService.clearSettlements(confirm, enterpriseId)));
    }

    @Operation(summary = "清空全部数据（需 confirm=CLEAR_ALL，按企业隔离）")
    @PostMapping("/clear-all")
    @PreAuthorize("hasAuthority('system:clear')")
    public R<Map<String, Object>> clearAll(
            @RequestParam(value = "confirm", required = false) String confirm,
            @RequestParam(value = "enterpriseId", required = false) Long enterpriseId) {
        return R.ok(result(systemClearService.clearAll(confirm, enterpriseId)));
    }
}
