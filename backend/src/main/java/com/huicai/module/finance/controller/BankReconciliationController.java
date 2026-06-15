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

    @Operation(summary = "对账汇总 (含 PENDING_CONFIRM 统计)")
    @GetMapping("/summary")
    public R<Map<String, Object>> summary(
            @RequestParam Long accountId, @RequestParam String period) {
        return R.ok(service.summarize(accountId, period));
    }

    @Operation(summary = "未达账项 (4方向分类: BANK_RECEIPT/PAYMENT_ENTERPRISE_NOT, ENTERPRISE_RECEIPT/PAYMENT_BANK_NOT)")
    @GetMapping("/unmatched")
    public R<List<Map<String, Object>>> unmatched(
            @RequestParam Long accountId, @RequestParam String period) {
        return R.ok(service.unmatchedItems(accountId, period));
    }

    // ─── P4.1: 5维评分 ───

    @Operation(summary = "单笔 5 维评分 (金额/日期/名称/摘要/参考号)")
    @GetMapping("/score")
    public R<BankReconciliationService.ScoreResult> score(
            @RequestParam Long accountId,
            @RequestParam Long statementId,
            @RequestParam Long journalId) {
        return R.ok(service.calculateScore(accountId, statementId, journalId));
    }

    // ─── P4.2: 评分路由 ───

    @Operation(summary = "批量自动匹配 (≥85 自动 MATCHED, 60-84 PENDING_CONFIRM, <60 UNMATCHED)")
    @PostMapping("/run-matching")
    public R<List<BankReconciliationService.MatchResult>> runMatching(
            @RequestParam Long accountId, @RequestParam String period) {
        return R.ok(service.runMatching(accountId, period));
    }

    // ─── P4.4: 对账锁定 ───

    @Operation(summary = "获取对账锁")
    @PostMapping("/lock")
    public R<Boolean> lock(
            @RequestParam Long accountId,
            @RequestParam String period,
            @RequestParam String operator,
            @RequestParam(defaultValue = "300") long ttlSeconds) {
        return R.ok(service.lockReconciliation(accountId, period, operator, ttlSeconds));
    }

    @Operation(summary = "释放对账锁")
    @PostMapping("/unlock")
    public R<Void> unlock(
            @RequestParam Long accountId,
            @RequestParam String period,
            @RequestParam String operator) {
        service.unlockReconciliation(accountId, period, operator);
        return R.ok();
    }

    @Operation(summary = "P14-1 人工确认匹配 (PENDING_CONFIRM → MATCHED)")
    @PostMapping("/confirm")
    public R<BankReconciliationService.ConfirmResult> confirm(
            @RequestParam Long statementId,
            @RequestParam Long journalId,
            @RequestParam(required = false) String operator) {
        return R.ok(service.confirmMatch(statementId, journalId, operator == null ? "system" : operator));
    }

    @Operation(summary = "P14-1 人工驳回匹配 (PENDING_CONFIRM → UNMATCHED)")
    @PostMapping("/reject")
    public R<BankReconciliationService.ConfirmResult> reject(
            @RequestParam Long statementId,
            @RequestParam Long journalId,
            @RequestParam(required = false) String operator) {
        return R.ok(service.rejectMatch(statementId, journalId, operator == null ? "system" : operator));
    }
}
