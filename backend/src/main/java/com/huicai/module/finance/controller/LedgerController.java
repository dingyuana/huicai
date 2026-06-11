package com.huicai.module.finance.controller;

import com.huicai.common.response.R;
import com.huicai.module.finance.service.LedgerService;
import com.huicai.module.finance.service.SubjectBalanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "账簿查询")
@RestController
@RequestMapping("/api/v1/ledgers")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;
    private final SubjectBalanceService subjectBalanceService;

    @Operation(summary = "科目余额表")
    @GetMapping("/subject-balance")
    public R<List<Map<String, Object>>> subjectBalance(@RequestParam String period) {
        return R.ok(ledgerService.subjectBalance(period));
    }

    @Operation(summary = "总分类账")
    @GetMapping("/general")
    public R<List<Map<String, Object>>> generalLedger(@RequestParam Long subjectId, @RequestParam String period) {
        return R.ok(ledgerService.generalLedger(subjectId, period));
    }

    @Operation(summary = "明细账")
    @GetMapping("/subsidiary")
    public R<List<Map<String, Object>>> subsidiaryLedger(@RequestParam Long subjectId, @RequestParam String period) {
        return R.ok(ledgerService.subsidiaryLedger(subjectId, period));
    }

    @Operation(summary = "试算平衡")
    @GetMapping("/trial-balance")
    public R<Map<String, Object>> trialBalance(@RequestParam String period) {
        return R.ok(subjectBalanceService.checkTrialBalance(period));
    }
}
