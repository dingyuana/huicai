package com.huicai.base.system.controller;

import com.huicai.common.response.R;
import com.huicai.base.system.aspect.Log;
import com.huicai.base.system.dto.DeptSaveDTO;
import com.huicai.base.system.entity.DeptEntity;
import jakarta.validation.Valid;
import com.huicai.base.system.service.DeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/system/dept")
@RequiredArgsConstructor
public class DeptController {

    private final DeptService deptService;

    @GetMapping("/tree")
    public R<?> tree() {
        return R.ok(deptService.getDeptTree());
    }

    @GetMapping("/{id}")
    public R<?> get(@PathVariable Long id) {
        return R.ok(deptService.getById(id));
    }

    @PostMapping
    @Log(value = "新增部门", module = "system")
    public R<?> create(@Valid @RequestBody DeptSaveDTO dto) {
        deptService.create(dto.toEntity());
        return R.ok();
    }

    @PutMapping("/{id}")
    @Log(value = "修改部门", module = "system")
    public R<?> update(@PathVariable Long id, @Valid @RequestBody DeptSaveDTO dto) {
        DeptEntity dept = dto.toEntity();
        dept.setId(id);
        deptService.update(dept);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Log(value = "删除部门", module = "system")
    public R<?> delete(@PathVariable Long id) {
        deptService.delete(id);
        return R.ok();
    }
}
