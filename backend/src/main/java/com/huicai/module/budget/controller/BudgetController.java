package com.huicai.module.budget.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.module.budget.entity.BudgetAdjustmentEntity;
import com.huicai.module.budget.entity.BudgetEntity;
import com.huicai.module.budget.entity.BudgetEntryEntity;
import com.huicai.module.budget.service.BudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Tag(name = "预算管理")
@RestController
@RequestMapping("/api/v1/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService service;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<IPage<BudgetEntity>> page(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageQuery(period, status, current, size));
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<BudgetEntity> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "创建预算")
    @PostMapping
    public R<BudgetEntity> create(@RequestBody CreateRequest request) {
        return R.ok(service.create(request.budget, request.entries));
    }

    @Operation(summary = "提交预算")
    @PostMapping("/{id}/submit")
    public R<BudgetEntity> submit(@PathVariable Long id) {
        return R.ok(service.submit(id));
    }

    @Operation(summary = "审批预算")
    @PostMapping("/{id}/approve")
    public R<BudgetEntity> approve(@PathVariable Long id) {
        return R.ok(service.approve(id));
    }

    @Operation(summary = "激活预算")
    @PostMapping("/{id}/activate")
    public R<BudgetEntity> activate(@PathVariable Long id) {
        return R.ok(service.activate(id));
    }

    @Operation(summary = "预算检查(用于单据/凭证保存时校验)")
    @GetMapping("/check")
    public R<Map<String, Object>> check(
            @RequestParam Long subjectId,
            @RequestParam String period,
            @RequestParam BigDecimal amount) {
        return R.ok(service.checkBudget(subjectId, period, amount));
    }

    @Operation(summary = "执行分析")
    @GetMapping("/execution")
    public R<Map<String, Object>> execution(@RequestParam String period) {
        return R.ok(service.executionAnalysis(period));
    }

    @Operation(summary = "调整分页")
    @GetMapping("/adjustments/page")
    public R<IPage<BudgetAdjustmentEntity>> pageAdjustment(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageQueryAdjustment(status, current, size));
    }

    @Operation(summary = "创建调整")
    @PostMapping("/adjustments")
    public R<BudgetAdjustmentEntity> createAdjustment(@RequestBody BudgetAdjustmentEntity entity) {
        return R.ok(service.createAdjustment(entity));
    }

    @Operation(summary = "审批调整")
    @PostMapping("/adjustments/{id}/approve")
    public R<BudgetAdjustmentEntity> approveAdjustment(@PathVariable Long id) {
        return R.ok(service.approveAdjustment(id));
    }

    public static class CreateRequest {
        public BudgetEntity budget;
        public List<BudgetEntryEntity> entries;
    }
}
