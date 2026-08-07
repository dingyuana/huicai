package com.huicai.base.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.response.R;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.service.PeriodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "会计期间管理")
@RestController
@RequestMapping("/api/v1/periods")
@RequiredArgsConstructor
public class PeriodController {

    private final PeriodService periodService;

    @Operation(summary = "获取期间列表(分页)")
    @GetMapping
    public R<IPage<PeriodEntity>> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(periodService.page(new Page<>(current, size)));
    }

    @Operation(summary = "获取期间列表(全量)")
    @GetMapping("/all")
    public R<List<PeriodEntity>> listAll() {
        return R.ok(periodService.list(new LambdaQueryWrapper<PeriodEntity>()
                .orderByDesc(PeriodEntity::getPeriodCode)));
    }

    @Operation(summary = "获取期间列表(全量, /list 路径兼容前端)")
    @GetMapping("/list")
    public R<List<PeriodEntity>> listAllNamed() {
        return R.ok(periodService.list(new LambdaQueryWrapper<PeriodEntity>()
                .orderByDesc(PeriodEntity::getPeriodCode)));
    }

    @Operation(summary = "新增期间")
    @PostMapping
    public R<PeriodEntity> create(@Valid @RequestBody PeriodEntity period) {
        periodService.save(period);
        return R.ok(period);
    }

    @Operation(summary = "修改期间")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody PeriodEntity period) {
        period.setId(id);
        periodService.updateById(period);
        return R.ok();
    }

    @Operation(summary = "删除期间")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        periodService.removeById(id);
        return R.ok();
    }

    @Operation(summary = "启用期间")
    @PostMapping("/{id}/open")
    public R<Void> openPeriod(@PathVariable Long id) {
        periodService.openPeriod(id);
        return R.ok();
    }

    @Operation(summary = "关闭期间")
    @PostMapping("/{id}/close")
    public R<Void> closePeriod(@PathVariable Long id) {
        periodService.closePeriod(id);
        return R.ok();
    }

    @Operation(summary = "锁定期间")
    @PostMapping("/{id}/lock")
    public R<Void> lockPeriod(@PathVariable Long id) {
        periodService.lockPeriod(id);
        return R.ok();
    }

    @Operation(summary = "解锁期间")
    @PostMapping("/{id}/unlock")
    public R<Void> unlockPeriod(@PathVariable Long id) {
        periodService.unlockPeriod(id);
        return R.ok();
    }

    @Operation(summary = "获取期间详情")
    @GetMapping("/{id}")
    public R<PeriodEntity> getById(@PathVariable Long id) {
        return R.ok(periodService.getById(id));
    }
}
