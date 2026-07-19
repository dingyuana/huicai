package com.huicai.sme.asset.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.sme.asset.entity.AssetCategoryEntity;
import com.huicai.sme.asset.service.AssetCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "资产类别")
@RestController
@RequestMapping("/api/v1/asset-categories")
@RequiredArgsConstructor
public class AssetCategoryController {

    private final AssetCategoryService service;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<IPage<AssetCategoryEntity>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageQuery(keyword, current, size));
    }

    @Operation(summary = "查询全部")
    @GetMapping("/list")
    public R<List<AssetCategoryEntity>> list() {
        return R.ok(service.listAll());
    }

    @Operation(summary = "根据ID查询")
    @GetMapping("/{id}")
    public R<AssetCategoryEntity> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "创建")
    @PostMapping
    public R<AssetCategoryEntity> create(@RequestBody AssetCategoryEntity entity) {
        return R.ok(service.create(entity));
    }

    @Operation(summary = "更新")
    @PutMapping("/{id}")
    public R<AssetCategoryEntity> update(@PathVariable Long id, @RequestBody AssetCategoryEntity entity) {
        entity.setId(id);
        return R.ok(service.update(entity));
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
