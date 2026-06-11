package com.huicai.module.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.response.R;
import com.huicai.module.system.entity.VoucherTypeEntity;
import com.huicai.module.system.service.VoucherTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "凭证类型管理")
@RestController
@RequestMapping("/api/v1/voucher-types")
@RequiredArgsConstructor
public class VoucherTypeController {

    private final VoucherTypeService voucherTypeService;

    @Operation(summary = "获取凭证类型列表(分页)")
    @GetMapping
    public R<IPage<VoucherTypeEntity>> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(voucherTypeService.page(new Page<>(current, size)));
    }

    @Operation(summary = "获取凭证类型列表(全量)")
    @GetMapping("/all")
    public R<List<VoucherTypeEntity>> listAll() {
        return R.ok(voucherTypeService.list());
    }

    @Operation(summary = "新增凭证类型")
    @PostMapping
    public R<VoucherTypeEntity> create(@Valid @RequestBody VoucherTypeEntity voucherType) {
        voucherTypeService.save(voucherType);
        return R.ok(voucherType);
    }

    @Operation(summary = "修改凭证类型")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody VoucherTypeEntity voucherType) {
        voucherType.setId(id);
        voucherTypeService.updateById(voucherType);
        return R.ok();
    }

    @Operation(summary = "删除凭证类型")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        voucherTypeService.removeById(id);
        return R.ok();
    }

    @Operation(summary = "获取凭证类型详情")
    @GetMapping("/{id}")
    public R<VoucherTypeEntity> getById(@PathVariable Long id) {
        return R.ok(voucherTypeService.getById(id));
    }
}
