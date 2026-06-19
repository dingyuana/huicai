package com.huicai.module.arap.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.module.arap.entity.ArapSettlementEntity;
import com.huicai.module.arap.entity.ArapSettlementEntryEntity;
import com.huicai.module.arap.service.ArapSettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "核销管理")
@RestController
@RequestMapping("/api/v1/arap-settlements")
@RequiredArgsConstructor
public class ArapSettlementController {

    private final ArapSettlementService service;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<IPage<ArapSettlementEntity>> page(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageQuery(status, current, size));
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<ArapSettlementEntity> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "创建核销单")
    @PostMapping
    public R<ArapSettlementEntity> create(@RequestBody CreateRequest request) {
        return R.ok(service.create(request.settlement, request.entries));
    }

    @Operation(summary = "确认核销")
    @PostMapping("/{id}/confirm")
    public R<ArapSettlementEntity> confirm(@PathVariable Long id) {
        return R.ok(service.confirm(id));
    }

    @Operation(summary = "生成凭证 — CONFIRMED → VOUCHERED")
    @PostMapping("/{id}/generate-voucher")
    public R<Void> generateVoucher(@PathVariable Long id) {
        service.generateVoucher(id);
        return R.ok();
    }

    @Operation(summary = "反核销 — CONFIRMED → REVERSED")
    @PostMapping("/{id}/reverse")
    public R<Void> reverse(@PathVariable Long id) {
        service.reverse(id);
        return R.ok();
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    public static class CreateRequest {
        public ArapSettlementEntity settlement;
        public List<ArapSettlementEntryEntity> entries;
    }
}
