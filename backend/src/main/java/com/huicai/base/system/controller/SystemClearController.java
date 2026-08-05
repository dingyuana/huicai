package com.huicai.base.system.controller;

import com.huicai.common.response.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Tag(name = "数据维护")
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemClearController {

    private final JdbcTemplate jdbcTemplate;

    private Map<String, Object> result(int deleted, String message) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("deleted", deleted);
        r.put("message", message);
        return r;
    }

    @Operation(summary = "清空银行流水")
    @PostMapping("/clear-bank-statements")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> clearBankStatements() {
        int deleted = jdbcTemplate.update("DELETE FROM t_bank_statement");
        log.info("清空银行流水: deleted={}", deleted);
        return R.ok(result(deleted, "清空银行流水 " + deleted + " 条"));
    }

    @Operation(summary = "清空发票记录")
    @PostMapping("/clear-invoice-records")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> clearInvoiceRecords() {
        int d1 = jdbcTemplate.update("DELETE FROM t_input_invoice");
        int d2 = jdbcTemplate.update("DELETE FROM t_output_invoice");
        int total = d1 + d2;
        log.info("清空发票记录: input={}, output={}", d1, d2);
        return R.ok(result(total, "清空发票记录 " + total + " 条"));
    }

    @Operation(summary = "清空所有凭证")
    @PostMapping("/clear-vouchers")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> clearVouchers() {
        int d1 = jdbcTemplate.update("DELETE FROM t_voucher_entry");
        int d2 = jdbcTemplate.update("DELETE FROM t_voucher");
        int d3 = jdbcTemplate.update("DELETE FROM t_subject_balance");
        int d4 = jdbcTemplate.update("DELETE FROM t_voucher_cash_flow");
        int total = d1 + d2 + d3 + d4;
        log.info("清空凭证: entries={}, vouchers={}, balance={}, cash_flow={}", d1, d2, d3, d4);
        return R.ok(result(total, "清空凭证 " + total + " 条"));
    }

    @Operation(summary = "清空报表数据")
    @PostMapping("/clear-report-data")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> clearReportData() {
        int d1 = jdbcTemplate.update("DELETE FROM t_subject_balance");
        int d2 = jdbcTemplate.update("DELETE FROM t_voucher_cash_flow");
        int total = d1 + d2;
        log.info("清空报表数据: subject_balance={}, cash_flow={}", d1, d2);
        return R.ok(result(total, "清空报表数据 " + total + " 条"));
    }

    @Operation(summary = "报表数据统计")
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

    @Operation(summary = "清空业务单据")
    @PostMapping("/clear-business-docs")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> clearBusinessDocs() {
        // 先清理引用业务单据的外键行（fk_settle_entry_doc / fk_aging_alert_doc 无级联删除）
        int d1 = jdbcTemplate.update("DELETE FROM t_arap_settlement_entry");
        int d2 = jdbcTemplate.update("DELETE FROM t_arap_settlement");
        int d3 = jdbcTemplate.update("DELETE FROM t_reconciliation_log");
        int d4 = jdbcTemplate.update("DELETE FROM t_aging_alert");
        // 保留银行流水/凭证：解绑 business_doc_id 引用而非删除记录
        int d5 = jdbcTemplate.update("UPDATE t_bank_journal SET business_doc_id = NULL WHERE business_doc_id IS NOT NULL");
        int d6 = jdbcTemplate.update("UPDATE t_voucher SET business_doc_id = NULL WHERE business_doc_id IS NOT NULL");
        int d7 = jdbcTemplate.update("DELETE FROM t_business_doc_entry");
        int d8 = jdbcTemplate.update("DELETE FROM t_business_doc");
        int total = d1 + d2 + d3 + d4 + d5 + d6 + d7 + d8;
        log.info("清空业务单据: settlement_entry={}, settlement={}, recon_log={}, aging_alert={}, bank_journal_unlink={}, voucher_unlink={}, doc_entry={}, doc={}",
                d1, d2, d3, d4, d5, d6, d7, d8);
        return R.ok(result(total, "清空业务单据 " + total + " 条"));
    }

    @Operation(summary = "清空应收相关数据（业务单据+票据+核销）")
    @PostMapping("/clear-receivables")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> clearReceivables() {
        int d1 = jdbcTemplate.update("DELETE FROM t_arap_settlement_entry");
        int d2 = jdbcTemplate.update("DELETE FROM t_arap_settlement");
        int d3 = jdbcTemplate.update("DELETE FROM t_reconciliation_log");
        int d4 = jdbcTemplate.update("DELETE FROM t_note_receivable");
        int d5 = jdbcTemplate.update("DELETE FROM t_business_doc_entry");
        int d6 = jdbcTemplate.update("DELETE FROM t_business_doc");
        int total = d1 + d2 + d3 + d4 + d5 + d6;
        log.info("清空应收: settlement_entry={}, settlement={}, recon_log={}, note_receivable={}, doc_entry={}, doc={}",
                d1, d2, d3, d4, d5, d6);
        return R.ok(result(total, "清空应收相关数据 " + total + " 条"));
    }

    @Operation(summary = "清空应付相关数据（业务单据+核销）")
    @PostMapping("/clear-payables")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> clearPayables() {
        int d1 = jdbcTemplate.update("DELETE FROM t_arap_settlement_entry");
        int d2 = jdbcTemplate.update("DELETE FROM t_arap_settlement");
        int d3 = jdbcTemplate.update("DELETE FROM t_reconciliation_log");
        int d4 = jdbcTemplate.update("DELETE FROM t_business_doc_entry");
        int d5 = jdbcTemplate.update("DELETE FROM t_business_doc");
        int total = d1 + d2 + d3 + d4 + d5;
        log.info("清空应付: settlement_entry={}, settlement={}, recon_log={}, doc_entry={}, doc={}",
                d1, d2, d3, d4, d5);
        return R.ok(result(total, "清空应付相关数据 " + total + " 条"));
    }

    @Operation(summary = "清空核销数据")
    @PostMapping("/clear-settlements")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> clearSettlements() {
        int d1 = jdbcTemplate.update("DELETE FROM t_arap_settlement_entry");
        int d2 = jdbcTemplate.update("DELETE FROM t_arap_settlement");
        int d3 = jdbcTemplate.update("DELETE FROM t_reconciliation_log");
        // 重置业务单据核销金额：未核销金额恢复为单据金额
        int d4 = jdbcTemplate.update(
                "UPDATE t_business_doc SET settled_amount = 0, unsettled_amount = amount WHERE settled_amount != 0 OR unsettled_amount != amount");
        int total = d1 + d2 + d3 + d4;
        log.info("清空核销数据: entries={}, settlements={}, recon_logs={}, docs_reset={}", d1, d2, d3, d4);
        return R.ok(result(total, "清空核销数据 " + total + " 条"));
    }

    @Operation(summary = "清空全部数据")
    @PostMapping("/clear-all")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> clearAll() {
        int total = 0;
        total += jdbcTemplate.update("DELETE FROM t_arap_settlement_entry");
        total += jdbcTemplate.update("DELETE FROM t_arap_settlement");
        total += jdbcTemplate.update("DELETE FROM t_reconciliation_log");
        total += jdbcTemplate.update("DELETE FROM t_note_receivable");
        total += jdbcTemplate.update("DELETE FROM t_business_doc_entry");
        total += jdbcTemplate.update("DELETE FROM t_business_doc");
        total += jdbcTemplate.update("DELETE FROM t_voucher_entry");
        total += jdbcTemplate.update("DELETE FROM t_voucher");
        // 同步清运行期报表数据, 避免 clear-all 后报表残留
        total += jdbcTemplate.update("DELETE FROM t_subject_balance");
        total += jdbcTemplate.update("DELETE FROM t_voucher_cash_flow");
        total += jdbcTemplate.update("DELETE FROM t_input_invoice");
        total += jdbcTemplate.update("DELETE FROM t_output_invoice");
        total += jdbcTemplate.update("DELETE FROM t_bank_statement");
        log.info("清空全部数据: total={}", total);
        return R.ok(result(total, "清空全部数据 " + total + " 条"));
    }
}