package com.huicai.module.system.controller;

import com.huicai.common.response.R;
import com.huicai.module.system.aspect.Log;
import com.huicai.module.system.entity.DeptEntity;
import com.huicai.module.system.service.DeptService;
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
    public R<?> create(@RequestBody DeptEntity dept) {
        deptService.create(dept);
        return R.ok();
    }

    @PutMapping("/{id}")
    @Log(value = "修改部门", module = "system")
    public R<?> update(@PathVariable Long id, @RequestBody DeptEntity dept) {
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
