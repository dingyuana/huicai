package com.huicai.base.auth.controller;

import com.huicai.common.response.R;
import com.huicai.module.system.aspect.Log;
import com.huicai.base.auth.entity.MenuEntity;
import com.huicai.base.auth.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/system/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @GetMapping("/tree")
    public R<?> tree() {
        return R.ok(menuService.getMenuTree());
    }

    @GetMapping("/options")
    public R<?> options() {
        return R.ok(menuService.getMenuOptions());
    }

    @GetMapping("/{id}")
    public R<?> get(@PathVariable Long id) {
        return R.ok(menuService.getById(id));
    }

    @PostMapping
    @Log(value = "新增菜单", module = "system")
    public R<?> create(@RequestBody MenuEntity menu) {
        menuService.create(menu);
        return R.ok();
    }

    @PutMapping("/{id}")
    @Log(value = "修改菜单", module = "system")
    public R<?> update(@PathVariable Long id, @RequestBody MenuEntity menu) {
        menu.setId(id);
        menuService.update(menu);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Log(value = "删除菜单", module = "system")
    public R<?> delete(@PathVariable Long id) {
        menuService.delete(id);
        return R.ok();
    }

    @GetMapping("/routes")
    public R<?> routes(Authentication authentication) {
        String username = authentication.getName();
        // userId is needed, this would be improved with a custom UserDetails
        // For now we get menu routes - simplified
        return R.ok(menuService.getMenuTree());
    }

    @GetMapping("/buttons")
    public R<?> buttons(Authentication authentication) {
        String username = authentication.getName();
        return R.ok(List.of());
    }
}
