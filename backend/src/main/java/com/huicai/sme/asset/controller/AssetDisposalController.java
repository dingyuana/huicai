package com.huicai.sme.asset.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.sme.asset.dto.AssetDisposalCreateDTO;
import com.huicai.sme.asset.vo.AssetDisposalVO;
import jakarta.validation.Valid;
import com.huicai.sme.asset.service.AssetDisposalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "资产处置")
@RestController
@RequestMapping("/api/sme/asset/v1/asset-disposals")
@RequiredArgsConstructor
public class AssetDisposalController {

    private final AssetDisposalService service;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<IPage<AssetDisposalVO>> page(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(AssetDisposalVO.from(service.pageQuery(status, current, size)));
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<AssetDisposalVO> getById(@PathVariable Long id) {
        return R.ok(AssetDisposalVO.from(service.getById(id)));
    }

    @Operation(summary = "创建")
    @PostMapping
    public R<AssetDisposalVO> create(@Valid @RequestBody AssetDisposalCreateDTO dto) {
        return R.ok(AssetDisposalVO.from(service.create(dto.toEntity())));
    }

    @Operation(summary = "审批")
    @PostMapping("/{id}/approve")
    public R<AssetDisposalVO> approve(@PathVariable Long id) {
        return R.ok(AssetDisposalVO.from(service.approve(id)));
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
