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
    void getRoutesByUserId_DIR作骨架收入_BUTTON仍排除() {
        // 翻面（V1.117）：DIR 是 buildTree 的内部骨架，滤掉则 MENU 行 parentId≠null
        // 无根可挂（实库顶层 8 行全是 DIR ⇒ 任何角色路由恒空）。BUTTON 仍排除。
        when(userRoleMapper.getRoleIdsByUserId(1L)).thenReturn(List.of(1L));
        when(roleMenuMapper.getMenuIdsByRoleId(1L)).thenReturn(List.of(1L, 2L, 3L));
        MenuEntity menu = stubEntity("MENU");
        MenuEntity dir = stubEntity("DIR");
        when(menuMapper.selectBatchIds(Set.of(1L, 2L, 3L))).thenReturn(List.of(
                menu, stubEntity("BUTTON"), dir));
        List<MenuEntity> result = service.getRoutesByUserId(1L);
        assertEquals(2, result.size(), "MENU 与 DIR（骨架）应进路由树，仅 BUTTON 排除");
        assertTrue(result.stream().anyMatch(m -> "DIR".equals(m.getType())));
        assertTrue(result.stream().noneMatch(m -> "BUTTON".equals(m.getType())));
    }

    @Test
    void getRoutesByUserId_只授子不授父_逐级回填DIR祖先() {
        // V1.117 核心守卫：授权行只有子 MENU(51)，父 DIR(5) 不在 selectBatchIds 结果里
        // ⇒ 必须经 selectById 逐级回填，树以 DIR 为根、MENU 挂其 children。
        // 反证：撤实现里的回填块 ⇒ 本例恰红（结果空列表）。
        when(userRoleMapper.getRoleIdsByUserId(1L)).thenReturn(List.of(1L));
        when(roleMenuMapper.getMenuIdsByRoleId(1L)).thenReturn(List.of(51L));
        MenuEntity child = stubEntity("MENU");
        child.setId(51L);
        child.setParentId(5L);
        when(menuMapper.selectBatchIds(Set.of(51L))).thenReturn(List.of(child));
        MenuEntity parent = stubEntity("DIR");
        parent.setId(5L);
        parent.setParentId(null);
        when(menuMapper.selectById(5L)).thenReturn(parent);

        List<MenuEntity> result = service.getRoutesByUserId(1L);
        assertEquals(1, result.size(), "回填父 DIR 后应以 DIR 为唯一根");
        assertEquals("DIR", result.get(0).getType());
        assertEquals(1, result.get(0).getChildren().size(), "子 MENU 应挂在回填 DIR 的 children");
        assertEquals(51L, result.get(0).getChildren().get(0).getId());
    }

    @Test
    void getRoutesByUserId_祖先链多级缺失_回填到根() {
        // 两级缺失：MENU(51, parent=5) → DIR(5, parent=2) → DIR(2, parent=null)
        // 回填必须逐级向上直到 parentId=null 的根，缺一级树就断一层。
        when(userRoleMapper.getRoleIdsByUserId(1L)).thenReturn(List.of(1L));
        when(roleMenuMapper.getMenuIdsByRoleId(1L)).thenReturn(List.of(51L));
        MenuEntity child = stubEntity("MENU");
        child.setId(51L);
        child.setParentId(5L);
        when(menuMapper.selectBatchIds(Set.of(51L))).thenReturn(List.of(child));
        MenuEntity mid = stubEntity("DIR");
        mid.setId(5L);
        mid.setParentId(2L);
        when(menuMapper.selectById(5L)).thenReturn(mid);
        MenuEntity root = stubEntity("DIR");
        root.setId(2L);
        root.setParentId(null);
        when(menuMapper.selectById(2L)).thenReturn(root);

        List<MenuEntity> result = service.getRoutesByUserId(1L);
        assertEquals(1, result.size(), "回填链应到达 parentId=null 的根");
        assertEquals(2L, result.get(0).getId(), "根是祖父 DIR(2)");
        assertEquals(1, result.get(0).getChildren().size());
        assertEquals(5L, result.get(0).getChildren().get(0).getId(), "第二层是 DIR(5)");
        assertEquals(51L, result.get(0).getChildren().get(0).getChildren().get(0).getId(),
                "第三层是原始授权的 MENU(51)");
    }

    @Test
    void getRoutesByUserId_祖先停用或不存在_静默跳过不炸() {
        // 负向：父行已删（selectById null）或停用——回填宽容处理，子行仍以平铺根出现，
        // 不能 NPE 也不能丢掉已授权的 MENU 行。
        when(userRoleMapper.getRoleIdsByUserId(1L)).thenReturn(List.of(1L));
        when(roleMenuMapper.getMenuIdsByRoleId(1L)).thenReturn(List.of(51L));
        MenuEntity child = stubEntity("MENU");
        child.setId(51L);
        child.setParentId(5L);
        when(menuMapper.selectBatchIds(Set.of(51L))).thenReturn(List.of(child));
        when(menuMapper.selectById(5L)).thenReturn(null);

        List<MenuEntity> result = service.getRoutesByUserId(1L);
        assertEquals(1, result.size(), "父缺失不吞掉已授权的子 MENU 行");
        assertEquals(51L, result.get(0).getId());
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
