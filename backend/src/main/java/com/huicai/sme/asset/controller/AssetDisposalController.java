package com.huicai.sme.asset.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.sme.asset.entity.AssetDisposalEntity;
import com.huicai.sme.asset.service.AssetDisposalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "资产处置")
@RestController
@RequestMapping("/api/v1/asset-disposals")
@RequiredArgsConstructor
public class AssetDisposalController {

    private final AssetDisposalService service;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<IPage<AssetDisposalEntity>> page(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageQuery(status, current, size));
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<AssetDisposalEntity> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "创建")
    @PostMapping
    public R<AssetDisposalEntity> create(@RequestBody AssetDisposalEntity entity) {
        return R.ok(service.create(entity));
    }

    @Operation(summary = "审批")
    @PostMapping("/{id}/approve")
    public R<AssetDisposalEntity> approve(@PathVariable Long id) {
        return R.ok(service.approve(id));
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
