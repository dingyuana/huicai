package com.huicai.base.auth.service;

import com.huicai.base.auth.entity.MenuEntity;

import java.util.List;

public interface MenuService {
    List<MenuEntity> getMenuTree();
    List<MenuEntity> getMenuOptions();
    MenuEntity getById(Long id);
    void create(MenuEntity menu);
    void update(MenuEntity menu);
    void delete(Long id);
    List<MenuEntity> getRoutesByUserId(Long userId);
    List<String> getUserButtonPermissions(Long userId);
    List<Long> getMenuIdsByRoleId(Long roleId);
}
