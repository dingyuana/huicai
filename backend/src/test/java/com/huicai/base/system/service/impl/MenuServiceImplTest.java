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
        MenuEntity e = new MenuEntity();
        e.setId(1L);
        e.setName("测试菜单");
        e.setPermissionCode("system:test");
        e.setPath("/test");
        e.setType("menu");
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
        assertNotNull(result);
        verify(menuMapper).selectBatchIds(any());
    }
}
