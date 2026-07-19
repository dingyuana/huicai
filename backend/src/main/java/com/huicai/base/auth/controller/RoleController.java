package com.huicai.base.auth.controller;

import com.huicai.common.response.R;
import com.huicai.base.audit.aspect.Log;
import com.huicai.base.auth.entity.RoleEntity;
import com.huicai.base.auth.service.MenuService;
import com.huicai.base.auth.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;
    private final MenuService menuService;

    @GetMapping("/page")
    public R<?> page(@RequestParam(defaultValue = "1") long page,
                     @RequestParam(defaultValue = "10") long size,
                     @RequestParam(required = false) String keyword,
                     @RequestParam(required = false) String status) {
        return R.ok(roleService.pageRole(page, size, keyword, status));
    }

    @GetMapping("/{id}")
    public R<?> get(@PathVariable Long id) {
        return R.ok(roleService.getById(id));
    }

    @GetMapping("/{id}/menus")
    public R<?> getMenus(@PathVariable Long id) {
        return R.ok(menuService.getMenuIdsByRoleId(id));
    }

    @PutMapping("/{id}/menus")
    @Log(value = "分配角色菜单", module = "system")
    public R<?> assignMenus(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        roleService.assignMenus(id, body.get("menuIds"));
        return R.ok();
    }

    @PostMapping
    @Log(value = "新增角色", module = "system")
    public R<RoleEntity> create(@RequestBody RoleEntity role) {
        roleService.create(role);
        return R.ok(role);
    }

    @PutMapping("/{id}")
    @Log(value = "修改角色", module = "system")
    public R<?> update(@PathVariable Long id, @RequestBody RoleEntity role) {
        role.setId(id);
        roleService.update(role);
        return R.ok();
    }

    @PutMapping("/{id}/status")
    @Log(value = "修改角色状态", module = "system")
    public R<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        roleService.updateStatus(id, body.get("status"));
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Log(value = "删除角色", module = "system")
    public R<?> delete(@PathVariable Long id) {
        roleService.delete(id);
        return R.ok();
    }
}
