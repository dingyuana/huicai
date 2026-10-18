package com.huicai.sme.asset.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.sme.asset.entity.AssetInventoryEntryEntity;
import com.huicai.sme.asset.entity.AssetInventoryEntity;
import com.huicai.sme.asset.service.AssetInventoryService;
import com.huicai.sme.asset.vo.AssetInventoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "资产盘点")
@RestController
@RequestMapping("/api/sme/asset/v1/asset-inventories")
@RequiredArgsConstructor
public class AssetInventoryController {

    private final AssetInventoryService service;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<IPage<AssetInventoryVO>> page(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(AssetInventoryVO.from(service.pageQuery(status, current, size)));
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<AssetInventoryVO> getById(@PathVariable Long id) {
        return R.ok(AssetInventoryVO.from(service.getById(id)));
    }

    @Operation(summary = "创建盘点单")
    @PostMapping
    public R<AssetInventoryVO> create(@RequestBody CreateRequest request) {
        return R.ok(AssetInventoryVO.from(service.create(request.inventory, request.entries)));
    }

    @Operation(summary = "完成盘点(录入实盘数)")
    @PostMapping("/{id}/complete")
    public R<AssetInventoryVO> complete(
            @PathVariable Long id, @RequestBody List<AssetInventoryEntryEntity> entries) {
        return R.ok(AssetInventoryVO.from(service.complete(id, entries)));
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    public static class CreateRequest {
        public AssetInventoryEntity inventory;
        public List<AssetInventoryEntryEntity> entries;
    }
}
