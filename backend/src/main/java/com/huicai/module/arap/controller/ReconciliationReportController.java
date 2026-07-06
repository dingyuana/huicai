package com.huicai.module.arap.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.response.R;
import com.huicai.module.arap.constant.ArapStatus;
import com.huicai.module.arap.entity.ReconciliationLogEntity;
import com.huicai.module.arap.mapper.ReconciliationLogMapper;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.mapper.BusinessDocMapper;
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

    private final BusinessDocMapper businessDocMapper;
    private final ReconciliationLogMapper logMapper;

    // ==================== 未核销明细 ====================

    @Operation(summary = "未核销应收明细 — t_business_doc (customerId != null) unsettledAmount > 0")
    @GetMapping("/unmatched-receivables")
    public R<IPage<BusinessDocEntity>> unmatchedReceivables(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateBefore,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        Page<BusinessDocEntity> page = new Page<>(current, size);
        LambdaQueryWrapper<BusinessDocEntity> wrapper = new LambdaQueryWrapper<BusinessDocEntity>()
                .in(BusinessDocEntity::getDocType, "INVOICE_OUT", "RECEIPT")
                .gt(BusinessDocEntity::getUnsettledAmount, BigDecimal.ZERO)
                .eq(customerId != null, BusinessDocEntity::getCustomerId, customerId)
                .eq(period != null, BusinessDocEntity::getPeriod, period)
                .le(dueDateBefore != null, BusinessDocEntity::getDueDate, dueDateBefore)
                .orderByAsc(BusinessDocEntity::getDueDate, BusinessDocEntity::getDocDate);
        return R.ok(businessDocMapper.selectPage(page, wrapper));
    }

    @Operation(summary = "未核销应付明细 — t_business_doc (supplierId != null) unsettledAmount > 0")
    @GetMapping("/unmatched-payables")
    public R<IPage<BusinessDocEntity>> unmatchedPayables(
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateBefore,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        Page<BusinessDocEntity> page = new Page<>(current, size);
        LambdaQueryWrapper<BusinessDocEntity> wrapper = new LambdaQueryWrapper<BusinessDocEntity>()
                .in(BusinessDocEntity::getDocType, "INVOICE_IN", "PAYMENT")
                .gt(BusinessDocEntity::getUnsettledAmount, BigDecimal.ZERO)
                .eq(vendorId != null, BusinessDocEntity::getSupplierId, vendorId)
                .eq(period != null, BusinessDocEntity::getPeriod, period)
                .le(dueDateBefore != null, BusinessDocEntity::getDueDate, dueDateBefore)
                .orderByAsc(BusinessDocEntity::getDueDate, BusinessDocEntity::getDocDate);
        return R.ok(businessDocMapper.selectPage(page, wrapper));
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
