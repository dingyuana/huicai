package com.huicai.module.arap.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.module.arap.entity.PayableEntity;
import com.huicai.module.arap.service.PayableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "应付明细")
@RestController
@RequestMapping("/api/v1/payables")
@RequiredArgsConstructor
public class PayableController {

    private final PayableService service;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<IPage<PayableEntity>> page(
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageQuery(vendorId, period, current, size));
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<PayableEntity> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "创建")
    @PostMapping
    public R<PayableEntity> create(@RequestBody PayableEntity entity) {
        return R.ok(service.create(entity));
    }

    @Operation(summary = "供应商账龄分析")
    @GetMapping("/aging")
    public R<Map<String, Object>> aging(@RequestParam(required = false) Long vendorId) {
        if (vendorId == null) {
            return R.ok(java.util.Map.of("message", "请指定供应商ID"));
        }
        return R.ok(service.agingAnalysis(vendorId));
    }
}
