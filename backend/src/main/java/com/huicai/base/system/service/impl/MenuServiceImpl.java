package com.huicai.base.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
    public void delete(Long id) {
        List<MenuEntity> allMenus = menuMapper.selectList(
                new LambdaQueryWrapper<MenuEntity>()
                        .eq(MenuEntity::getDeleted, 0));
        List<Long> childIds = getChildIds(allMenus, id);
        childIds.add(id);

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
