package com.huicai.module.asset.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.module.asset.entity.AssetCardEntity;
import com.huicai.module.asset.service.AssetCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "资产卡片")
@RestController
@RequestMapping("/api/v1/asset-cards")
@RequiredArgsConstructor
public class AssetCardController {

    private final AssetCardService service;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<IPage<AssetCardEntity>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageQuery(keyword, status, categoryId, current, size));
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<AssetCardEntity> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "创建")
    @PostMapping
    public R<AssetCardEntity> create(@RequestBody AssetCardEntity entity) {
        return R.ok(service.create(entity));
    }

    @Operation(summary = "更新")
    @PutMapping("/{id}")
    public R<AssetCardEntity> update(@PathVariable Long id, @RequestBody AssetCardEntity entity) {
        entity.setId(id);
        return R.ok(service.update(entity));
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    @Operation(summary = "计算指定期间折旧")
    @GetMapping("/{id}/depreciation")
    public R<java.math.BigDecimal> calculateDepreciation(
            @PathVariable Long id, @RequestParam String period) {
        AssetCardEntity card = service.getById(id);
        return R.ok(service.calculateDepreciation(card, period));
    }

    @Operation(summary = "计提指定期间全部资产折旧")
    @PostMapping("/depreciate/{period}")
    public R<Void> depreciatePeriod(@PathVariable String period) {
        service.depreciatePeriod(period);
        return R.ok();
    }

    @Operation(summary = "计提单资产折旧")
    @PostMapping("/{id}/depreciate")
    public R<Void> depreciateOne(@PathVariable Long id, @RequestParam String period) {
        service.depreciateOne(id, period);
        return R.ok();
    }

    @Operation(summary = "最近卡片")
    @GetMapping("/recent")
    public R<List<Map<String, Object>>> recent(@RequestParam(defaultValue = "10") int limit) {
        return R.ok(service.recentCards(limit));
    }
}
