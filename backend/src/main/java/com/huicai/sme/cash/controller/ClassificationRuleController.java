package com.huicai.sme.cash.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.sme.cash.entity.ClassificationRuleEntity;
import com.huicai.sme.cash.service.ClassificationRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "分类规则管理")
@RestController
@RequestMapping("/api/v1/classification-rules")
@RequiredArgsConstructor
public class ClassificationRuleController {

    private final ClassificationRuleService service;

    @Operation(summary = "规则列表分页")
    @GetMapping
    public R<IPage<ClassificationRuleEntity>> page(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.page(tenantId, current, size));
    }

    @Operation(summary = "规则详情")
    @GetMapping("/{id}")
    public R<ClassificationRuleEntity> getById(@PathVariable Long id) {
        ClassificationRuleEntity entity = service.getById(id);
        if (entity == null) {
            return R.badRequest("规则不存在");
        }
        return R.ok(entity);
    }

    @Operation(summary = "创建规则")
    @PostMapping
    public R<ClassificationRuleEntity> create(@RequestBody ClassificationRuleEntity entity) {
        return R.ok(service.create(entity));
    }

    @Operation(summary = "更新规则")
    @PutMapping("/{id}")
    public R<ClassificationRuleEntity> update(@PathVariable Long id, @RequestBody ClassificationRuleEntity entity) {
        ClassificationRuleEntity updated = service.update(id, entity);
        if (updated == null) {
            return R.badRequest("规则不存在");
        }
        return R.ok(updated);
    }

    @Operation(summary = "删除规则")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    @Operation(summary = "拖拽排序")
    @PostMapping("/reorder")
    public R<Void> reorder(@RequestBody List<Long> ids) {
        service.reorder(ids);
        return R.ok();
    }

    @Operation(summary = "初始化种子规则")
    @PostMapping("/seed")
    public R<Integer> seed(@RequestParam Long tenantId) {
        int count = service.seedForNewTenant(tenantId);
        return R.ok("已插入 " + count + " 条种子规则", count);
    }

    @Operation(summary = "单笔测试匹配")
    @PostMapping("/match")
    public R<ClassificationRuleEntity> match(@RequestParam String description,
                                              @RequestParam(required = false) String direction,
                                              @RequestParam(required = false) String counterparty) {
        return R.ok(service.match(description, direction, counterparty));
    }
}
