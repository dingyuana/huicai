package com.huicai.base.voucher.controller;

import com.huicai.common.response.R;
import com.huicai.base.voucher.dto.vo.AuxiliaryLedgerRowVO;
import com.huicai.base.voucher.service.LedgerService;
import com.huicai.base.balance.service.SubjectBalanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "账簿查询")
@RestController
@RequestMapping("/api/base/voucher/v1/ledgers")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;
    private final SubjectBalanceService subjectBalanceService;

    @Operation(summary = "科目余额表")
    @GetMapping("/subject-balance")
    public R<List<Map<String, Object>>> subjectBalance(
            @RequestParam String period,
            @RequestParam(required = false, defaultValue = "false") boolean includeZero,
            @RequestParam(required = false, defaultValue = "false") boolean includeNoMovement,
            @RequestParam(required = false) String subjectCodePrefix) {
        return R.ok(ledgerService.subjectBalance(period, includeZero, includeNoMovement, subjectCodePrefix));
    }

    @Operation(summary = "总分类账")
    @GetMapping("/general")
    public R<List<Map<String, Object>>> generalLedger(
            @RequestParam Long subjectId,
            @RequestParam String period,
            @RequestParam(required = false, defaultValue = "false") boolean includeUnposted) {
        return R.ok(ledgerService.generalLedger(subjectId, period, includeUnposted));
    }

    @Operation(summary = "明细账")
    @GetMapping("/subsidiary")
    public R<List<Map<String, Object>>> subsidiaryLedger(
            @RequestParam Long subjectId,
            @RequestParam String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "false") boolean includeUnposted) {
        return R.ok(ledgerService.subsidiaryLedger(subjectId, period, startDate, endDate, includeUnposted));
    }

    @Operation(summary = "辅助核算账")
    @GetMapping("/auxiliary")
    public R<List<AuxiliaryLedgerRowVO>> auxiliaryLedger(
            @RequestParam String dimensionType,
            @RequestParam String period,
            @RequestParam(required = false) Long dimensionValue) {
        return R.ok(ledgerService.auxiliaryLedger(dimensionType, period, dimensionValue));
    }

    @Operation(summary = "试算平衡")
    @GetMapping("/trial-balance")
    public R<Map<String, Object>> trialBalance(@RequestParam String period) {
        return R.ok(subjectBalanceService.checkTrialBalance(period));
    }
}
