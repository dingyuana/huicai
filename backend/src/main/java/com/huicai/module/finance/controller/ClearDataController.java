package com.huicai.module.finance.controller;

import com.huicai.common.response.R;
import com.huicai.module.finance.service.impl.ClearDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "数据维护")
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class ClearDataController {

    private final ClearDataService clearDataService;

    @Operation(summary = "清空银行流水")
    @PostMapping("/clear-bank-statements")
    public R<Map<String, Object>> clearBankStatements() {
        int count = clearDataService.clearBankStatements();
        return R.ok(Map.of("deleted", count, "message", "已清空银行流水及相关数据"));
    }

    @Operation(summary = "清空发票导入记录")
    @PostMapping("/clear-invoice-records")
    public R<Map<String, Object>> clearInvoiceRecords() {
        int count = clearDataService.clearInvoiceRecords();
        return R.ok(Map.of("deleted", count, "message", "已清空发票导入记录及相关数据"));
    }

    @Operation(summary = "清空全部数据(流水+发票+凭证)")
    @PostMapping("/clear-all")
    public R<Map<String, Object>> clearAll() {
        int count = clearDataService.clearAll();
        return R.ok(Map.of("deleted", count, "message", "已清空全部数据"));
    }

    @Operation(summary = "清空所有凭证(保留业务单据和流水)")
    @PostMapping("/clear-vouchers")
    public R<Map<String, Object>> clearVouchers() {
        int count = clearDataService.clearVouchers();
        return R.ok(Map.of("deleted", count, "message", "已清空所有凭证及相关引用"));
    }

    @Operation(summary = "清空业务单据(含明细行)")
    @PostMapping("/clear-business-docs")
    public R<Map<String, Object>> clearBusinessDocs() {
        int count = clearDataService.clearBusinessDocs();
        return R.ok(Map.of("deleted", count, "message", "已清空所有业务单据及明细行"));
    }

    @Operation(summary = "清空核销数据")
    @PostMapping("/clear-settlements")
    public R<Map<String, Object>> clearSettlements() {
        int count = clearDataService.clearSettlements();
        return R.ok(Map.of("deleted", count, "message", "已清空核销数据，业务单据核销金额已重置"));
    }
}