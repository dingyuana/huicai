package com.huicai.base.system.controller;

import com.huicai.common.response.R;
import com.huicai.base.system.aspect.Log;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.base.system.service.RoleService;
import com.huicai.base.system.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final RoleService roleService;

    @GetMapping("/page")
    public R<?> page(@RequestParam(defaultValue = "1") long page,
                     @RequestParam(defaultValue = "10") long size,
                     @RequestParam(required = false) String keyword,
                     @RequestParam(required = false) Long deptId,
                     @RequestParam(required = false) String status) {
        return R.ok(userService.pageUser(page, size, keyword, deptId, status));
    }

    @GetMapping("/{id}")
    public R<?> get(@PathVariable Long id) {
        return R.ok(userService.getById(id));
    }

    @PostMapping
    @Log(value = "新增用户", module = "system")
    public R<Long> create(@RequestBody UserEntity user) {
        userService.create(user);
        return R.ok(user.getId());
    }

    @PutMapping("/{id}")
    @Log(value = "修改用户", module = "system")
    public R<?> update(@PathVariable Long id, @RequestBody UserEntity user) {
        user.setId(id);
        userService.update(user);
        return R.ok();
    }

    @PutMapping("/{id}/status")
    @Log(value = "修改用户状态", module = "system")
    public R<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        userService.updateStatus(id, body.get("status"));
        return R.ok();
    }

    @PutMapping("/{id}/reset-pwd")
    @Log(value = "重置用户密码", module = "system")
    public R<?> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        userService.resetPassword(id, body.get("newPassword"));
        return R.ok();
    }

    @PutMapping("/{id}/roles")
    @Log(value = "分配用户角色", module = "system")
    public R<?> assignRoles(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        userService.assignRoles(id, body.get("roleIds"));
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Log(value = "删除用户", module = "system")
    public R<?> delete(@PathVariable Long id) {
        userService.delete(id);
        return R.ok();
    }

    @GetMapping("/roles")
    public R<?> allRoles() {
        return R.ok(roleService.listAll());
    }
}
