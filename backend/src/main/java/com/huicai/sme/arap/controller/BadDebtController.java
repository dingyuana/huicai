package com.huicai.sme.arap.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.sme.arap.entity.BadDebtProvisionEntity;
import com.huicai.sme.arap.service.BadDebtService;
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

    @Operation(summary = "确认（自动生成凭证）")
    @PostMapping("/{id}/confirm")
    public R<BadDebtProvisionEntity> confirm(@PathVariable Long id,
                                             @RequestParam(defaultValue = "1") Long userId) {
        return R.ok(service.confirm(id, userId));
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    // ===== P43 新增端点 =====

    @Operation(summary = "获取默认计提方案")
    @GetMapping("/scheme")
    public R<?> getDefaultScheme() {
        return R.ok(service.getDefaultScheme());
    }

    @Operation(summary = "更新默认方案计提比例")
    @PutMapping("/scheme")
    public R<Void> updateSchemeRatios(@RequestBody Map<String, BigDecimal> ratios) {
        service.updateSchemeRatios(ratios);
        return R.ok();
    }

    @Operation(summary = "坏账核销")
    @PostMapping("/write-off")
    public R<Void> writeOff(@RequestParam Long sourceId,
                            @RequestParam String sourceType,
                            @RequestParam BigDecimal amount,
                            @RequestParam String reason,
                            @RequestParam(defaultValue = "1") Long userId) {
        return R.ok(service.writeOff(sourceId, sourceType, amount, reason, userId));
    }

    @Operation(summary = "已核销收回")
    @PostMapping("/recovery")
    public R<Void> recovery(@RequestParam Long sourceId,
                            @RequestParam BigDecimal amount,
                            @RequestParam(defaultValue = "1") Long userId) {
        return R.ok(service.recovery(sourceId, amount, userId));
    }
}