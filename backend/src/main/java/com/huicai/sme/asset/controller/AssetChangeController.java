package com.huicai.sme.asset.controller;

import com.huicai.base.system.util.SecurityUtils;
import com.huicai.common.response.R;
import com.huicai.sme.asset.dto.AssetChangeCreateDTO;
import com.huicai.sme.asset.service.AssetChangeService;
import com.huicai.sme.asset.vo.AssetChangeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SPEC-P144（D-4）：资产变动（调整）端点。
 *
 * <p>读权限复用 {@code asset:card:list}（变动是卡面子数据，避免菜单行膨胀）；
 * 写权限独立成码 {@code asset:change:create}（V190 动作级权限码口径）——
 * 授权见 V196（ACCOUNTANT/ADMIN/FINANCE_MGR，同 asset:card:list 持有者）。</p>
 */
@Tag(name = "资产变动")
@RestController
@RequestMapping("/api/sme/asset/v1/asset-changes")
@RequiredArgsConstructor
public class AssetChangeController {

    private final AssetChangeService assetChangeService;

    @Operation(summary = "登记资产变动（VALUE_ADJUST 生成调整凭证）")
    @PreAuthorize("hasAuthority('asset:change:create')")
    @PostMapping
    public R<Long> create(@Valid @RequestBody AssetChangeCreateDTO dto) {
        return R.ok(assetChangeService.create(dto, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "某资产的变动流水（含凭证号回显）")
    @PreAuthorize("hasAuthority('asset:card:list')")
    @GetMapping
    public R<List<AssetChangeVO>> listByAsset(@RequestParam Long assetId) {
        return R.ok(assetChangeService.listByAsset(assetId));
    }
}
