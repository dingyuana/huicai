package com.huicai.sme.asset.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.sme.asset.entity.AssetCategoryEntity;
import com.huicai.sme.asset.service.AssetCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.huicai.sme.asset.vo.AssetCategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.huicai.sme.asset.dto.AssetCategorySaveDTO;
import jakarta.validation.Valid;

@Tag(name = "资产类别")
@RestController
@RequestMapping("/api/sme/asset/v1/asset-categories")
@RequiredArgsConstructor
public class AssetCategoryController {

    private final AssetCategoryService service;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<IPage<com.huicai.sme.asset.vo.AssetCategoryVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(AssetCategoryVO.from(service.pageQuery(keyword, current, size)));
    }

    @Operation(summary = "查询全部")
    @GetMapping("/list")
    public R<List<com.huicai.sme.asset.vo.AssetCategoryVO>> list() {
        return R.ok(AssetCategoryVO.from(service.listAll()));
    }

    @Operation(summary = "根据ID查询")
    @GetMapping("/{id}")
    public R<com.huicai.sme.asset.vo.AssetCategoryVO> getById(@PathVariable Long id) {
        return R.ok(AssetCategoryVO.from(service.getById(id)));
    }

    @Operation(summary = "创建")
    @PostMapping
    public R<com.huicai.sme.asset.vo.AssetCategoryVO> create(@Valid @RequestBody AssetCategorySaveDTO dto) {
        AssetCategoryEntity entity = dto.toEntity();
        return R.ok(AssetCategoryVO.from(service.create(entity)));
    }

    @Operation(summary = "更新")
    @PutMapping("/{id}")
    public R<com.huicai.sme.asset.vo.AssetCategoryVO> update(@PathVariable Long id, @Valid @RequestBody AssetCategorySaveDTO dto) {
        AssetCategoryEntity entity = dto.toEntity();
        entity.setId(id);
        return R.ok(AssetCategoryVO.from(service.update(entity)));
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
