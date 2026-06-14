package com.huicai.module.arap.controller;

import com.huicai.common.response.R;
import com.huicai.module.arap.entity.ReconciliationLogEntity;
import com.huicai.module.arap.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Tag(name = "智能核销")
@RestController
@RequestMapping("/api/v1/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    @Operation(summary = "收款核销推荐")
    @PostMapping("/receipt/{receiptId}/recommend")
    public R<ReconciliationService.RecommendResult> recommendReceipt(
            @PathVariable Long receiptId,
            @RequestParam Long customerId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String summary,
            @RequestParam(required = false) String counterpartyName) {
        return R.ok(reconciliationService.recommendReceipt(receiptId, customerId, amount, summary, counterpartyName));
    }

    @Operation(summary = "付款核销推荐")
    @PostMapping("/payment/{paymentId}/recommend")
    public R<ReconciliationService.RecommendResult> recommendPayment(
            @PathVariable Long paymentId,
            @RequestParam Long vendorId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String summary,
            @RequestParam(required = false) String counterpartyName) {
        return R.ok(reconciliationService.recommendPayment(paymentId, vendorId, amount, summary, counterpartyName));
    }

    @Operation(summary = "银行流水自动核销推荐")
    @PostMapping("/auto-recommend/{statementId}")
    public R<ReconciliationService.RecommendResult> recommendForStatement(
            @PathVariable Long statementId,
            @RequestParam Long accountId,
            @RequestParam String direction,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String counterpartyName,
            @RequestParam(required = false) String summary) {
        return R.ok(reconciliationService.recommendForStatement(statementId, accountId, direction, amount, counterpartyName, summary));
    }

    @Operation(summary = "执行单笔核销")
    @PostMapping("/execute")
    public R<ReconciliationLogEntity> execute(@RequestBody ReconciliationService.ExecuteRequest request) {
        return R.ok(reconciliationService.execute(request));
    }

    @Operation(summary = "批量核销")
    @PostMapping("/batch-execute")
    public R<List<ReconciliationLogEntity>> batchExecute(@RequestBody List<ReconciliationService.ExecuteRequest> requests) {
        return R.ok(reconciliationService.batchExecute(requests));
    }

    @Operation(summary = "查询核销记录")
    @GetMapping("/records")
    public R<List<ReconciliationLogEntity>> getRecords(@RequestParam String sourceDocType, @RequestParam Long sourceDocId) {
        return R.ok(reconciliationService.getRecords(sourceDocType, sourceDocId));
    }

    @Operation(summary = "分页查询核销日志")
    @GetMapping("/logs/page")
    public R<com.baomidou.mybatisplus.core.metadata.IPage<ReconciliationLogEntity>> pageLogs(
            @RequestParam(required = false) String sourceDocType,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(reconciliationService.pageLogs(sourceDocType, current, size));
    }

    @Operation(summary = "反核销")
    @PostMapping("/{id}/reverse")
    public R<Void> reverse(@PathVariable Long id) {
        reconciliationService.reverse(id);
        return R.ok();
    }
}