package com.huicai.sme.arap.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.sme.arap.entity.CustomerStatementEntity;
import com.huicai.sme.arap.service.CustomerStatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "客户对账与差异处理")
@RestController
@RequestMapping("/api/sme/arap/v1")
@RequiredArgsConstructor
public class CustomerStatementController {

    private final CustomerStatementService service;

    // ==================== 对账单 ====================

    @Operation(summary = "生成对账单")
    @PostMapping("/customer-statements/generate")
    public R<List<CustomerStatementEntity>> generate(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> customerIds = (List<Long>) body.get("customerIds");
        String period = (String) body.get("period");
        return R.ok(service.generateStatements(customerIds, period));
    }

    @Operation(summary = "对账单详情")
    @GetMapping("/customer-statements/{id}")
    public R<CustomerStatementEntity> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "对账单分页查询")
    @GetMapping("/customer-statements/page")
    public R<IPage<CustomerStatementEntity>> page(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageQuery(status, current, size));
    }

    @Operation(summary = "发送对账单")
    @PostMapping("/customer-statements/{id}/send")
    public R<Void> send(@PathVariable Long id) {
        service.send(id);
        return R.ok();
    }

    @Operation(summary = "确认对账单")
    @PostMapping("/customer-statements/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id) {
        service.confirm(id);
        return R.ok();
    }

    @Operation(summary = "发起差异")
    @PostMapping("/customer-statements/{id}/dispute")
    public R<Void> dispute(@PathVariable Long id, @RequestBody CustomerStatementService.DisputeRequest request) {
        service.dispute(id, request);
        return R.ok();
    }

    // ==================== 未达账项 ====================

    @Operation(summary = "未达账项查询")
    @GetMapping("/outstanding-items")
    public R<IPage<CustomerStatementService.OutstandingItemVO>> pageOutstandingItems(
            @RequestParam(required = false) Long statementId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageOutstandingItems(statementId, customerId, status, current, size));
    }

    @Operation(summary = "解决未达账项")
    @PostMapping("/outstanding-items/{id}/resolve")
    public R<Void> resolveOutstandingItem(@PathVariable Long id) {
        service.resolveOutstandingItem(id);
        return R.ok();
    }

    @Operation(summary = "取消未达账项")
    @PostMapping("/outstanding-items/{id}/cancel")
    public R<Void> cancelOutstandingItem(@PathVariable Long id) {
        service.cancelOutstandingItem(id);
        return R.ok();
    }

    // ==================== 差异记录 ====================

    @Operation(summary = "差异记录查询")
    @GetMapping("/disputes")
    public R<IPage<CustomerStatementService.DisputeVO>> pageDisputes(
            @RequestParam(required = false) Long statementId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String disputeType,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageDisputes(statementId, customerId, disputeType, current, size));
    }

    @Operation(summary = "解决差异")
    @PostMapping("/disputes/{id}/resolve")
    public R<Void> resolveDispute(@PathVariable Long id, @RequestBody Map<String, String> body) {
        service.resolveDispute(id, body.get("resolution"));
        return R.ok();
    }
}