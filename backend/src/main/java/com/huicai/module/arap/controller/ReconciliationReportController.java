package com.huicai.module.arap.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.response.R;
import com.huicai.module.arap.constant.ArapStatus;
import com.huicai.module.arap.entity.PayableEntity;
import com.huicai.module.arap.entity.ReceivableEntity;
import com.huicai.module.arap.entity.ReconciliationLogEntity;
import com.huicai.module.arap.mapper.PayableMapper;
import com.huicai.module.arap.mapper.ReceivableMapper;
import com.huicai.module.arap.mapper.ReconciliationLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "核销报表")
@RestController
@RequestMapping("/api/v1/reconciliation/report")
@RequiredArgsConstructor
public class ReconciliationReportController {

    private final ReceivableMapper receivableMapper;
    private final PayableMapper payableMapper;
    private final ReconciliationLogMapper logMapper;

    // ==================== 未核销明细 ====================

    @Operation(summary = "未核销应收明细 — unsettledAmount > 0 的应收列表")
    @GetMapping("/unmatched-receivables")
    public R<IPage<ReceivableEntity>> unmatchedReceivables(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateBefore,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        Page<ReceivableEntity> page = new Page<>(current, size);
        LambdaQueryWrapper<ReceivableEntity> wrapper = new LambdaQueryWrapper<ReceivableEntity>()
                .gt(ReceivableEntity::getUnsettledAmount, BigDecimal.ZERO)
                .eq(customerId != null, ReceivableEntity::getCustomerId, customerId)
                .eq(period != null, ReceivableEntity::getPeriod, period)
                .le(dueDateBefore != null, ReceivableEntity::getDueDate, dueDateBefore)
                .orderByAsc(ReceivableEntity::getDueDate, ReceivableEntity::getTxDate);
        return R.ok(receivableMapper.selectPage(page, wrapper));
    }

    @Operation(summary = "未核销应付明细 — unsettledAmount > 0 的应付列表")
    @GetMapping("/unmatched-payables")
    public R<IPage<PayableEntity>> unmatchedPayables(
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateBefore,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        Page<PayableEntity> page = new Page<>(current, size);
        LambdaQueryWrapper<PayableEntity> wrapper = new LambdaQueryWrapper<PayableEntity>()
                .gt(PayableEntity::getUnsettledAmount, BigDecimal.ZERO)
                .eq(vendorId != null, PayableEntity::getVendorId, vendorId)
                .eq(period != null, PayableEntity::getPeriod, period)
                .le(dueDateBefore != null, PayableEntity::getDueDate, dueDateBefore)
                .orderByAsc(PayableEntity::getDueDate, PayableEntity::getTxDate);
        return R.ok(payableMapper.selectPage(page, wrapper));
    }

    // ==================== 核销操作日志 ====================

    @Operation(summary = "核销操作日志 — 按来源/目标/状态/时间范围查询")
    @GetMapping("/logs")
    public R<IPage<ReconciliationLogEntity>> reconciliationLogs(
            @RequestParam(required = false) String sourceDocType,
            @RequestParam(required = false) Long sourceDocId,
            @RequestParam(required = false) String targetDocType,
            @RequestParam(required = false) Long targetDocId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdBefore,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        Page<ReconciliationLogEntity> page = new Page<>(current, size);
        LambdaQueryWrapper<ReconciliationLogEntity> wrapper = new LambdaQueryWrapper<ReconciliationLogEntity>()
                .eq(sourceDocType != null, ReconciliationLogEntity::getSourceDocType, sourceDocType)
                .eq(sourceDocId != null, ReconciliationLogEntity::getSourceDocId, sourceDocId)
                .eq(targetDocType != null, ReconciliationLogEntity::getTargetDocType, targetDocType)
                .eq(targetDocId != null, ReconciliationLogEntity::getTargetDocId, targetDocId)
                .eq(status != null, ReconciliationLogEntity::getStatus, status)
                .ge(createdAfter != null, ReconciliationLogEntity::getCreatedAt, createdAfter)
                .le(createdBefore != null, ReconciliationLogEntity::getCreatedAt, createdBefore)
                .orderByDesc(ReconciliationLogEntity::getCreatedAt);
        return R.ok(logMapper.selectPage(page, wrapper));
    }

    @Operation(summary = "核销统计概览 — 按状态汇总核销金额")
    @GetMapping("/summary")
    public R<List<Map<String, Object>>> reconciliationSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdBefore) {
        LambdaQueryWrapper<ReconciliationLogEntity> wrapper = new LambdaQueryWrapper<ReconciliationLogEntity>()
                .ge(createdAfter != null, ReconciliationLogEntity::getCreatedAt, createdAfter)
                .le(createdBefore != null, ReconciliationLogEntity::getCreatedAt, createdBefore);
        List<ReconciliationLogEntity> all = logMapper.selectList(wrapper);

        Map<String, BigDecimal> summary = new java.util.LinkedHashMap<>();
        summary.put(ArapStatus.CONFIRMED, BigDecimal.ZERO);
        summary.put(ArapStatus.EXECUTED, BigDecimal.ZERO);
        summary.put(ArapStatus.REJECTED, BigDecimal.ZERO);
        summary.put(ArapStatus.CANCELLED, BigDecimal.ZERO);

        for (ReconciliationLogEntity log : all) {
            String st = log.getStatus() != null ? log.getStatus() : "UNKNOWN";
            summary.merge(st, log.getAllocatedAmount() != null ? log.getAllocatedAmount() : BigDecimal.ZERO, BigDecimal::add);
        }

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : summary.entrySet()) {
            result.add(Map.of("status", entry.getKey(), "totalAmount", entry.getValue()));
        }
        return R.ok(result);
    }
}
