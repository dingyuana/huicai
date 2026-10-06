package com.huicai.base.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.response.R;
import com.huicai.base.system.entity.SysConfigEntity;
import com.huicai.base.system.service.SysConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.huicai.base.system.vo.SysConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.huicai.base.system.dto.SysConfigSaveDTO;

@Tag(name = "系统参数管理")
@RestController
@RequestMapping("/api/v1/configs")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigService sysConfigService;

    @Operation(summary = "获取参数列表(分页)")
    @GetMapping
    public R<IPage<com.huicai.base.system.vo.SysConfigVO>> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(SysConfigVO.from(sysConfigService.page(new Page<>(current, size))));
    }

    @Operation(summary = "获取参数列表(全量)")
    @GetMapping("/all")
    public R<List<com.huicai.base.system.vo.SysConfigVO>> listAll() {
        return R.ok(SysConfigVO.from(sysConfigService.list()));
    }

    @Operation(summary = "批量获取参数值")
    @GetMapping("/values")
    public R<Map<String, String>> getValues(@RequestParam List<String> keys) {
        return R.ok(sysConfigService.getValues(keys));
    }

    @Operation(summary = "新增参数")
    @PostMapping
    public R<com.huicai.base.system.vo.SysConfigVO> create(@Valid @RequestBody SysConfigSaveDTO dto) {
        SysConfigEntity config = dto.toEntity();
        sysConfigService.save(config);
        return R.ok(SysConfigVO.from(config));
    }

    @Operation(summary = "修改参数")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody SysConfigSaveDTO dto) {
        SysConfigEntity config = dto.toEntity();
        config.setId(id);
        sysConfigService.updateById(config);
        return R.ok();
    }

    @Operation(summary = "删除参数")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        sysConfigService.removeById(id);
        return R.ok();
    }

    @Operation(summary = "获取参数详情")
    @GetMapping("/{id}")
    public R<com.huicai.base.system.vo.SysConfigVO> getById(@PathVariable Long id) {
        return R.ok(SysConfigVO.from(sysConfigService.getById(id)));
    }
}
