package com.huicai.module.arap.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.response.R;
import com.huicai.module.arap.entity.EmployeeEntity;
import com.huicai.module.arap.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "员工档案 - P11-1")
@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService service;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<IPage<EmployeeEntity>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(service.pageQuery(keyword, isActive, current, size));
    }

    @Operation(summary = "查询全部在职员工")
    @GetMapping("/list")
    public R<List<EmployeeEntity>> list() {
        return R.ok(service.listAll());
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<EmployeeEntity> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "按姓名查询（P11-3 银行流水匹配用）")
    @GetMapping("/by-name")
    public R<EmployeeEntity> byName(@RequestParam String name) {
        return R.ok(service.findByName(name));
    }

    @Operation(summary = "创建")
    @PostMapping
    public R<EmployeeEntity> create(@RequestBody EmployeeEntity entity) {
        return R.ok(service.create(entity));
    }

    @Operation(summary = "更新")
    @PutMapping("/{id}")
    public R<EmployeeEntity> update(@PathVariable Long id, @RequestBody EmployeeEntity entity) {
        entity.setId(id);
        return R.ok(service.update(entity));
    }

    @Operation(summary = "删除（逻辑）")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
