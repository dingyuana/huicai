package com.huicai.base.system.service.impl;

import com.huicai.base.system.entity.MenuEntity;
import com.huicai.base.system.mapper.MenuMapper;
import com.huicai.base.system.mapper.RoleMenuMapper;
import com.huicai.base.system.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MenuServiceImplTest {

    @Mock private MenuMapper menuMapper;
    @Mock private RoleMenuMapper roleMenuMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @InjectMocks private MenuServiceImpl service;

    private MenuEntity stubEntity() {
        return stubEntity("MENU");
    }

    /**
     * menu_type 必须是 DB 真实取值：**大写** MENU / BUTTON / DIR（见 chk_menu_type）。
     * 原 fixture 写小写 "menu"，与生产代码里同样的小写字面量「互相印证」，
     * 致使 getRoutesByUserId 永远过滤掉全部菜单这个缺陷从未被测试发现
     * （AGENTS §4.3 第 6 条「测试假阳性」）。
     */
    private MenuEntity stubEntity(String type) {
        MenuEntity e = new MenuEntity();
        e.setId(1L);
        e.setName("测试菜单");
        e.setPermissionCode("system:test");
        e.setPath("/test");
        e.setType(type);
        e.setSortOrder(1);
        e.setParentId(null);
        e.setIsActive(true);
        e.setIsVisible(true);
        return e;
    }

    @Test
    void getById_存在_返回Entity() {
        when(menuMapper.selectById(1L)).thenReturn(stubEntity());
        MenuEntity result = service.getById(1L);
        assertNotNull(result);
    }

    @Test
    void getById_不存在_返回Null() {
        when(menuMapper.selectById(99L)).thenReturn(null);
        assertNull(service.getById(99L));
    }

    @Test
    void create_正常_调insert() {
        service.create(stubEntity());
        verify(menuMapper).insert(any(MenuEntity.class));
    }

    @Test
    void getMenuTree_调selectList() {
        when(menuMapper.selectList(any())).thenReturn(java.util.Collections.singletonList(stubEntity()));
        service.getMenuTree();
        verify(menuMapper).selectList(any());
    }

    @Test
    void getRoutesByUserId_调selectBatchIds() {
        when(userRoleMapper.getRoleIdsByUserId(1L)).thenReturn(List.of(1L));
        when(roleMenuMapper.getMenuIdsByRoleId(1L)).thenReturn(List.of(1L));
        when(menuMapper.selectBatchIds(Set.of(1L))).thenReturn(List.of(stubEntity()));
        List<MenuEntity> result = service.getRoutesByUserId(1L);
        // 原断言只有 assertNotNull(result) —— 而错误实现返回的是「空列表」，非 null，
        // 断言照样通过（测试假阳性）。这里必须断言内容，否则大小写缺陷仍抓不到。
        assertEquals(1, result.size(), "menu_type=MENU 的菜单应进入路由");
        assertEquals("测试菜单", result.get(0).getName());
        verify(menuMapper).selectBatchIds(any());
    }

    @Test
    void getRoutesByUserId_只收MENU_排除BUTTON与DIR() {
        when(userRoleMapper.getRoleIdsByUserId(1L)).thenReturn(List.of(1L));
        when(roleMenuMapper.getMenuIdsByRoleId(1L)).thenReturn(List.of(1L, 2L, 3L));
        when(menuMapper.selectBatchIds(Set.of(1L, 2L, 3L))).thenReturn(List.of(
                stubEntity("MENU"), stubEntity("BUTTON"), stubEntity("DIR")));
        List<MenuEntity> result = service.getRoutesByUserId(1L);
        assertEquals(1, result.size(), "仅 MENU 应进路由，BUTTON/DIR 必须被过滤");
        assertEquals("MENU", result.get(0).getType());
    }

    @Test
    void getRoutesByUserId_小写menu_type不应被匹配() {
        // 负向断言：DB 与 CHECK 都只允许大写，小写值（脏数据）不构成有效路由节点
        when(userRoleMapper.getRoleIdsByUserId(1L)).thenReturn(List.of(1L));
        when(roleMenuMapper.getMenuIdsByRoleId(1L)).thenReturn(List.of(1L));
        when(menuMapper.selectBatchIds(Set.of(1L))).thenReturn(List.of(stubEntity("menu")));
        List<MenuEntity> result = service.getRoutesByUserId(1L);
        assertTrue(result.isEmpty(), "小写 menu 不应被当作 MENU 匹配");
    }
}
