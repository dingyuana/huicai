package com.huicai.base.system.controller;

import com.huicai.common.response.R;
import com.huicai.base.system.aspect.Log;
import com.huicai.base.system.entity.MenuEntity;
import com.huicai.base.system.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.huicai.base.system.dto.MenuSaveDTO;
import jakarta.validation.Valid;

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
    public R<?> create(@Valid @RequestBody MenuSaveDTO dto) {
        MenuEntity menu = dto.toEntity();
        menuService.create(menu);
        return R.ok();
    }

    @PutMapping("/{id}")
    @Log(value = "修改菜单", module = "system")
    public R<?> update(@PathVariable Long id, @Valid @RequestBody MenuSaveDTO dto) {
        MenuEntity menu = dto.toEntity();
        menu.setId(id);
        menuService.update(menu);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Log(value = "删除菜单", module = "system")
    // 止血（P133 之前）：该删除操作原先<b>无任何权限保护</b>，
    // 而 SecurityConfig 只到 anyRequest().authenticated() ⇒ 任何已登录用户都能删。
    // ⚠️ 复用**页面级**权限码做粗粒度闸门（「能进该页 = 能做该页操作」），
    //   真正的动作级模型见 SPEC-P133 权限清单。
    @PreAuthorize("hasAuthority('system:menu:list')")
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
