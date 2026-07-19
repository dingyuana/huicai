package com.huicai.sme.arap.controller;

import com.huicai.common.response.R;
import com.huicai.sme.arap.dto.ReconciliationToleranceDTO;
import com.huicai.sme.arap.dto.vo.ReconciliationToleranceVO;
import com.huicai.sme.arap.dto.vo.ReconciliationTraceVO;
import com.huicai.sme.arap.entity.ReconciliationExceptionEntity;
import com.huicai.sme.arap.entity.ReconciliationLogEntity;
import com.huicai.sme.arap.service.ReconciliationService;
import com.huicai.sme.arap.service.ReconciliationToleranceService;
import com.huicai.sme.arap.service.impl.ReconciliationServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
    private final ReconciliationToleranceService toleranceService;
    private final ReconciliationServiceImpl reconciliationServiceImpl;

    @Operation(summary = "收款核销推荐")
    @PostMapping("/receipt/{receiptId}/recommend")
    public R<ReconciliationService.RecommendResult> recommendReceipt(
            @PathVariable Long receiptId,
            @RequestParam String sourceDocType,
            @RequestParam Long customerId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String summary,
            @RequestParam(required = false) String counterpartyName) {
        return R.ok(reconciliationService.recommendReceipt(receiptId, sourceDocType, customerId, amount, summary, counterpartyName));
    }

    @Operation(summary = "付款核销推荐")
    @PostMapping("/payment/{paymentId}/recommend")
    public R<ReconciliationService.RecommendResult> recommendPayment(
            @PathVariable Long paymentId,
            @RequestParam String sourceDocType,
            @RequestParam Long vendorId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String summary,
            @RequestParam(required = false) String counterpartyName) {
        return R.ok(reconciliationService.recommendPayment(paymentId, sourceDocType, vendorId, amount, summary, counterpartyName));
    }

    @Operation(summary = "核销前预检查 (5项检查)")
    @PostMapping("/pre-check")
    public R<ReconciliationService.PreCheckResult> preCheck(@RequestBody ReconciliationService.ExecuteRequest request) {
        return R.ok(reconciliationService.preCheck(request));
    }

    @Operation(summary = "执行单笔核销")
    @PostMapping("/execute")
    public R<ReconciliationLogEntity> execute(@RequestBody ReconciliationService.ExecuteRequest request) {
        return R.ok(reconciliationService.execute(request));
    }

    @Operation(summary = "带差额调整的核销 (如手续费/折扣/尾差)")
    @PostMapping("/execute-with-adjustment")
    public R<ReconciliationLogEntity> executeWithAdjustment(
            @RequestBody ReconciliationService.ExecuteRequest request,
            @RequestParam BigDecimal adjustAmount,
            @RequestParam String adjustType,
            @RequestParam(defaultValue = "0") Long adjustSubjectId) {
        return R.ok(reconciliationService.executeWithAdjustment(request, adjustAmount, adjustType, adjustSubjectId));
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

    @Operation(summary = "反核销 (需原因)")
    @PostMapping("/{id}/reverse")
    public R<Void> reverse(@PathVariable Long id, @RequestParam String reason) {
        reconciliationService.reverse(id, reason);
        return R.ok();
    }

    @Operation(summary = "审批执行核销 (CONFIRMED → EXECUTED)")
    @PostMapping("/{id}/approve")
    public R<ReconciliationLogEntity> approve(@PathVariable Long id) {
        return R.ok(reconciliationService.approve(id));
    }

    @Operation(summary = "驳回核销 (CONFIRMED → REJECTED, 恢复应收/应付未结金额)")
    @PostMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestParam(required = false) String reason) {
        reconciliationService.reject(id, reason);
        return R.ok();
    }

    // ==================== 异常池管理 ====================

    @Operation(summary = "分页查询核销异常池")
    @GetMapping("/exceptions/page")
    public R<com.baomidou.mybatisplus.core.metadata.IPage<ReconciliationExceptionEntity>> pageExceptions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String exceptionType,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(reconciliationService.pageExceptions(status, exceptionType, current, size));
    }

    @Operation(summary = "解决异常 (OPEN → RESOLVED)")
    @PostMapping("/exceptions/{id}/resolve")
    public R<Void> resolveException(@PathVariable Long id,
                                    @RequestParam(defaultValue = "0") Long userId,
                                    @RequestParam(required = false) String remark) {
        reconciliationService.resolveException(id, userId, remark);
        return R.ok();
    }

    @Operation(summary = "忽略异常 (OPEN → IGNORED)")
    @PostMapping("/exceptions/{id}/ignore")
    public R<Void> ignoreException(@PathVariable Long id,
                                    @RequestParam(defaultValue = "0") Long userId,
                                    @RequestParam String reason) {
        reconciliationService.ignoreException(id, userId, reason);
        return R.ok();
    }

    @Operation(summary = "重试异常核销 (重新执行核销)")
    @PostMapping("/exceptions/{id}/retry")
    public R<ReconciliationLogEntity> retryException(@PathVariable Long id,
                                                      @RequestParam(defaultValue = "0") Long userId) {
        return R.ok(reconciliationService.retryException(id, userId));
    }

    // ==================== 多对多核销拓扑 ====================

    @Operation(summary = "FIFO 自动核销 — 按到期日优先核销最早未结清单据")
    @PostMapping("/auto-fifo")
    public R<List<ReconciliationLogEntity>> autoReconcileFifo(
            @RequestParam Long partyId,
            @RequestParam String targetDocType,
            @RequestParam BigDecimal amount,
            @RequestParam String sourceDocType,
            @RequestParam Long sourceDocId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String summary) {
        return R.ok(reconciliationService.autoReconcileFifo(
                partyId, targetDocType, amount, sourceDocType, sourceDocId, period, summary));
    }

    @Operation(summary = "拆分核销 — 一笔来源拆分核销多张目标单据")
    @PostMapping("/split-allocate")
    public R<List<ReconciliationLogEntity>> splitAllocate(
            @RequestParam String sourceDocType,
            @RequestParam Long sourceDocId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long vendorId,
            @RequestParam BigDecimal totalAmount,
            @RequestBody List<ReconciliationService.AllocationItem> allocations,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String summary) {
        return R.ok(reconciliationService.splitAllocate(
                sourceDocType, sourceDocId, customerId, vendorId,
                totalAmount, allocations, period, summary));
    }

    @Operation(summary = "智能最优匹配 — 自动 N:M 分配核销")
    @PostMapping("/smart-allocate")
    public R<List<ReconciliationLogEntity>> smartAllocate(
            @RequestParam String sourceDocType,
            @RequestParam Long sourceDocId,
            @RequestParam Long partyId,
            @RequestParam String partyType,
            @RequestParam String targetDocType,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String summary) {
        return R.ok(reconciliationService.smartAllocate(
                sourceDocType, sourceDocId, partyId, partyType,
                targetDocType, amount, period, summary));
    }

    // ==================== 全链路追溯 ====================

    @Operation(summary = "核销全链路追溯")
    @GetMapping("/{id}/trace")
    public R<ReconciliationTraceVO> trace(@PathVariable Long id) {
        return R.ok(reconciliationServiceImpl.trace(id));
    }

    // ==================== 容差配置管理 ====================

    @Operation(summary = "获取默认容差配置")
    @GetMapping("/tolerance/default")
    public R<ReconciliationToleranceVO> getDefaultTolerance() {
        return R.ok(toleranceService.getDefaultConfig());
    }

    @Operation(summary = "获取指定客户/供应商容差配置")
    @GetMapping("/tolerance/{partyId}/{partyType}")
    public R<ReconciliationToleranceVO> getToleranceByParty(
            @PathVariable Long partyId,
            @PathVariable String partyType) {
        return R.ok(toleranceService.getByParty(partyId, partyType));
    }

    @Operation(summary = "创建容差配置")
    @PostMapping("/tolerance")
    public R<ReconciliationToleranceVO> createTolerance(@Valid @RequestBody ReconciliationToleranceDTO dto) {
        return R.ok(toleranceService.create(dto));
    }

    @Operation(summary = "更新容差配置")
    @PutMapping("/tolerance/{id}")
    public R<ReconciliationToleranceVO> updateTolerance(
            @PathVariable Long id,
            @Valid @RequestBody ReconciliationToleranceDTO dto) {
        return R.ok(toleranceService.update(id, dto));
    }

    @Operation(summary = "删除容差配置")
    @DeleteMapping("/tolerance/{id}")
    public R<Void> deleteTolerance(@PathVariable Long id) {
        toleranceService.delete(id);
        return R.ok();
    }
}