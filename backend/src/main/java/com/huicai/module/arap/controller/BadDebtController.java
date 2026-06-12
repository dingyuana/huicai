package com.huicai.module.arap.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.module.arap.entity.BadDebtProvisionEntity;
import com.huicai.module.arap.service.BadDebtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@Tag(name = "坏账准备")
@RestController
@RequestMapping("/api/v1/bad-debts")
@RequiredArgsConstructor
public class BadDebtController {

    private final BadDebtService service;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<IPage<BadDebtProvisionEntity>> page(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageQuery(status, current, size));
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<BadDebtProvisionEntity> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "账龄比例法计提")
    @PostMapping("/provision/aging")
    public R<BadDebtProvisionEntity> provisionByAging(
            @RequestParam String period,
            @RequestBody Map<String, BigDecimal> ratios) {
        return R.ok(service.provisionByAging(period, ratios));
    }

    @Operation(summary = "余额百分比法计提")
    @PostMapping("/provision/percentage")
    public R<BadDebtProvisionEntity> provisionByPercentage(
            @RequestParam String period,
            @RequestParam BigDecimal ratio) {
        return R.ok(service.provisionByPercentage(period, ratio));
    }

    @Operation(summary = "确认")
    @PostMapping("/{id}/confirm")
    public R<BadDebtProvisionEntity> confirm(@PathVariable Long id) {
        return R.ok(service.confirm(id));
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
