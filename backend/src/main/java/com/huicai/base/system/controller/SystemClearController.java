package com.huicai.base.system.controller;

import com.huicai.common.response.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
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
        int d1 = jdbcTemplate.update("DELETE FROM t_input_invoice_detail");
        int d2 = jdbcTemplate.update("DELETE FROM t_input_invoice");
        int d3 = jdbcTemplate.update("DELETE FROM t_output_invoice_detail");
        int d4 = jdbcTemplate.update("DELETE FROM t_output_invoice");
        int total = d1 + d2 + d3 + d4;
        log.info("清空发票记录: input_detail={}, input={}, output_detail={}, output={}", d1, d2, d3, d4);
        return R.ok(result(total, "清空发票记录 " + total + " 条"));
    }

    @Operation(summary = "清空所有凭证")
    @PostMapping("/clear-vouchers")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> clearVouchers() {
        int d1 = jdbcTemplate.update("DELETE FROM t_voucher_entry");
        int d2 = jdbcTemplate.update("DELETE FROM t_voucher");
        int total = d1 + d2;
        log.info("清空凭证: entries={}, vouchers={}", d1, d2);
        return R.ok(result(total, "清空凭证 " + total + " 条"));
    }

    @Operation(summary = "清空业务单据")
    @PostMapping("/clear-business-docs")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> clearBusinessDocs() {
        int d1 = jdbcTemplate.update("DELETE FROM t_business_doc_item");
        int d2 = jdbcTemplate.update("DELETE FROM t_business_doc");
        int total = d1 + d2;
        log.info("清空业务单据: items={}, docs={}", d1, d2);
        return R.ok(result(total, "清空业务单据 " + total + " 条"));
    }

    @Operation(summary = "清空应收明细")
    @PostMapping("/clear-receivables")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> clearReceivables() {
        int d1 = jdbcTemplate.update("DELETE FROM t_reconciliation_detail");
        int d2 = jdbcTemplate.update("DELETE FROM t_reconciliation");
        int d3 = jdbcTemplate.update("DELETE FROM t_receivable");
        int total = d1 + d2 + d3;
        log.info("清空应收明细: reconciliation_detail={}, reconciliation={}, receivable={}", d1, d2, d3);
        return R.ok(result(total, "清空应收明细 " + total + " 条"));
    }

    @Operation(summary = "清空应付明细")
    @PostMapping("/clear-payables")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> clearPayables() {
        int d1 = jdbcTemplate.update("DELETE FROM t_reconciliation_detail");
        int d2 = jdbcTemplate.update("DELETE FROM t_reconciliation");
        int d3 = jdbcTemplate.update("DELETE FROM t_payable");
        int total = d1 + d2 + d3;
        log.info("清空应付明细: reconciliation_detail={}, reconciliation={}, payable={}", d1, d2, d3);
        return R.ok(result(total, "清空应付明细 " + total + " 条"));
    }

    @Operation(summary = "清空核销数据")
    @PostMapping("/clear-settlements")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> clearSettlements() {
        int d1 = jdbcTemplate.update("DELETE FROM t_arap_settlement_item");
        int d2 = jdbcTemplate.update("DELETE FROM t_arap_settlement_log");
        int d3 = jdbcTemplate.update("DELETE FROM t_arap_settlement");
        int total = d1 + d2 + d3;
        log.info("清空核销数据: items={}, logs={}, settlements={}", d1, d2, d3);
        return R.ok(result(total, "清空核销数据 " + total + " 条"));
    }

    @Operation(summary = "清空全部数据")
    @PostMapping("/clear-all")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> clearAll() {
        int total = 0;
        total += jdbcTemplate.update("DELETE FROM t_arap_settlement_item");
        total += jdbcTemplate.update("DELETE FROM t_arap_settlement_log");
        total += jdbcTemplate.update("DELETE FROM t_arap_settlement");
        total += jdbcTemplate.update("DELETE FROM t_reconciliation_detail");
        total += jdbcTemplate.update("DELETE FROM t_reconciliation");
        total += jdbcTemplate.update("DELETE FROM t_receivable");
        total += jdbcTemplate.update("DELETE FROM t_payable");
        total += jdbcTemplate.update("DELETE FROM t_business_doc_item");
        total += jdbcTemplate.update("DELETE FROM t_business_doc");
        total += jdbcTemplate.update("DELETE FROM t_voucher_entry");
        total += jdbcTemplate.update("DELETE FROM t_voucher");
        total += jdbcTemplate.update("DELETE FROM t_input_invoice_detail");
        total += jdbcTemplate.update("DELETE FROM t_input_invoice");
        total += jdbcTemplate.update("DELETE FROM t_output_invoice_detail");
        total += jdbcTemplate.update("DELETE FROM t_output_invoice");
        total += jdbcTemplate.update("DELETE FROM t_bank_statement");
        log.info("清空全部数据: total={}", total);
        return R.ok(result(total, "清空全部数据 " + total + " 条"));
    }
}