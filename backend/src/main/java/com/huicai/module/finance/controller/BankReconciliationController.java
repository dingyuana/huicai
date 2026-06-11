package com.huicai.module.finance.controller;

import com.huicai.common.response.R;
import com.huicai.module.finance.service.BankReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "银行对账")
@RestController
@RequestMapping("/api/v1/bank-reconciliation")
@RequiredArgsConstructor
public class BankReconciliationController {

    private final BankReconciliationService service;

    @Operation(summary = "余额调节表")
    @GetMapping("/adjustment")
    public R<Map<String, Object>> adjustment(
            @RequestParam Long accountId, @RequestParam String period) {
        return R.ok(service.generateAdjustment(accountId, period));
    }

    @Operation(summary = "对账汇总")
    @GetMapping("/summary")
    public R<Map<String, Object>> summary(
            @RequestParam Long accountId, @RequestParam String period) {
        return R.ok(service.summarize(accountId, period));
    }

    @Operation(summary = "未达账项")
    @GetMapping("/unmatched")
    public R<List<Map<String, Object>>> unmatched(
            @RequestParam Long accountId, @RequestParam String period) {
        return R.ok(service.unmatchedItems(accountId, period));
    }
}
