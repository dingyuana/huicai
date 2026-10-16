package com.huicai.base.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.security.ReferenceGuard;
import com.huicai.common.security.Ref;
import com.huicai.common.exception.BusinessException;
import com.huicai.base.system.entity.MenuEntity;
import com.huicai.base.system.entity.RoleMenuEntity;
import com.huicai.base.system.mapper.MenuMapper;
import com.huicai.base.system.mapper.RoleMenuMapper;
import com.huicai.base.system.mapper.UserRoleMapper;
import com.huicai.base.system.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
/**
 * 类级事务（P102/M5b，2026-10-03）：走租户表的路径必须有事务，否则
 * TenantRlsInitializer 切面不触发、app.enterprise_id 设不进去，
 * 应用以非超级用户连接时 RLS 会把本企业数据也过滤掉（读 0 行）。
 * 方法级 @Transactional 优先级更高，不受此影响。
 */
@Transactional
public class MenuServiceImpl implements MenuService {

    private final MenuMapper menuMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final UserRoleMapper userRoleMapper;

    @Override
    public List<MenuEntity> getMenuTree() {
        List<MenuEntity> allMenus = menuMapper.selectList(
                new LambdaQueryWrapper<MenuEntity>()
                        .eq(MenuEntity::getDeleted, 0)
                        .orderByAsc(MenuEntity::getSortOrder));
        return buildTree(allMenus, null);
    }

    @Override
    public List<MenuEntity> getMenuOptions() {
        return getMenuTree();
    }

    @Override
    public MenuEntity getById(Long id) {
        return menuMapper.selectById(id);
    }

    @Override
    public void create(MenuEntity menu) {
        menuMapper.insert(menu);
    }

    @Override
    public void update(MenuEntity menu) {
        menuMapper.updateById(menu);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ReferenceGuard(
            parent = "t_menu",
            label = "菜单",
            children = {
                    // 🔴 t_role_menu **没有 deleted 列** ⇒ 下面的级联是**硬删**、不可逆。
                    //    150 条角色授权因而被无声抹掉 —— 这是即时数据损失，不是「静默孤儿」。
                    @Ref(table = "t_role_menu", column = "menu_id",
                            label = "角色菜单授权", scopedByEnterprise = false),
            })
    public void delete(Long id) {
        List<MenuEntity> allMenus = menuMapper.selectList(
                new LambdaQueryWrapper<MenuEntity>()
                        .eq(MenuEntity::getDeleted, 0));
        List<Long> childIds = getChildIds(allMenus, id);
        childIds.add(id);

        // 🔴 @ReferenceGuard 只覆盖传入的 id，而本方法会删掉**整棵子树** ——
        // 子菜单上的角色授权同样会被硬删。守卫表达不了子树，故此处显式补齐。
        // 判据：删除范围大于守卫声明的 id 范围时，差集必须显式覆盖，
        // 否则「守卫看起来有」与「实际全都有」之间会差一截。
        List<Long> grantedChildIds = roleMenuMapper.selectList(
                        new LambdaQueryWrapper<RoleMenuEntity>().in(RoleMenuEntity::getMenuId, childIds))
                .stream().map(RoleMenuEntity::getMenuId).distinct().collect(Collectors.toList());
        if (!grantedChildIds.isEmpty()) {
            throw new BusinessException("该菜单及其子菜单已被角色授权使用，无法删除（共 "
                    + grantedChildIds.size() + " 个菜单存在授权）。请先取消授权。");
        }

        for (Long menuId : childIds) {
            roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenuEntity>().eq(RoleMenuEntity::getMenuId, menuId));
            menuMapper.deleteById(menuId);
        }
    }

    @Override
    public List<MenuEntity> getRoutesByUserId(Long userId) {
        List<Long> roleIds = userRoleMapper.getRoleIdsByUserId(userId);
        Set<Long> menuIds = roleIds.stream()
                .flatMap(roleId -> roleMenuMapper.getMenuIdsByRoleId(roleId).stream())
                .collect(Collectors.toSet());

        if (menuIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<MenuEntity> allMenus = menuMapper.selectBatchIds(menuIds).stream()
                // menu_type 是大写：DB 种子数据与 CHECK 约束 chk_menu_type 都只允许
                // MENU / BUTTON / DIR。原先写小写 "menu" 永远匹配不上，导致用户路由恒为空。
                .filter(m -> "MENU".equals(m.getType()) && m.getIsActive())
                .collect(Collectors.toList());

        return buildTree(allMenus, null);
    }

    @Override
    public List<String> getUserButtonPermissions(Long userId) {
        List<Long> roleIds = userRoleMapper.getRoleIdsByUserId(userId);
        Set<Long> menuIds = roleIds.stream()
                .flatMap(roleId -> roleMenuMapper.getMenuIdsByRoleId(roleId).stream())
                .collect(Collectors.toSet());

        if (menuIds.isEmpty()) {
            return new ArrayList<>();
        }

        return menuMapper.selectBatchIds(menuIds).stream()
                .filter(m -> m.getPermissionCode() != null)
                .map(MenuEntity::getPermissionCode)
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> getMenuIdsByRoleId(Long roleId) {
        return roleMenuMapper.getMenuIdsByRoleId(roleId);
    }

    private List<MenuEntity> buildTree(List<MenuEntity> allMenus, Long parentId) {
        List<MenuEntity> tree = new ArrayList<>();
        for (MenuEntity menu : allMenus) {
            if (parentId == null && menu.getParentId() == null ||
                parentId != null && parentId.equals(menu.getParentId())) {
                menu.setChildren(buildTree(allMenus, menu.getId()));
                tree.add(menu);
            }
        }
        return tree;
    }

    private List<Long> getChildIds(List<MenuEntity> allMenus, Long parentId) {
        List<Long> ids = new ArrayList<>();
        for (MenuEntity menu : allMenus) {
            if (parentId.equals(menu.getParentId())) {
                ids.add(menu.getId());
                ids.addAll(getChildIds(allMenus, menu.getId()));
            }
        }
        return ids;
    }
}
