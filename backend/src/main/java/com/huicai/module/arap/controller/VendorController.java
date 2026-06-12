package com.huicai.module.arap.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.module.arap.entity.VendorEntity;
import com.huicai.module.arap.service.VendorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "供应商档案")
@RestController
@RequestMapping("/api/v1/vendors")
@RequiredArgsConstructor
public class VendorController {

    private final VendorService service;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<IPage<VendorEntity>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageQuery(keyword, isActive, current, size));
    }

    @Operation(summary = "查询全部")
    @GetMapping("/list")
    public R<List<VendorEntity>> list() {
        return R.ok(service.listAll());
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<VendorEntity> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "创建")
    @PostMapping
    public R<VendorEntity> create(@RequestBody VendorEntity entity) {
        return R.ok(service.create(entity));
    }

    @Operation(summary = "更新")
    @PutMapping("/{id}")
    public R<VendorEntity> update(@PathVariable Long id, @RequestBody VendorEntity entity) {
        entity.setId(id);
        return R.ok(service.update(entity));
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    @Operation(summary = "供应商未结算汇总")
    @GetMapping("/unsettled-summary")
    public R<List<Map<String, Object>>> unsettledSummary() {
        return R.ok(service.unsettledSummary());
    }
}
