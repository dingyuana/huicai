package com.huicai.base.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.base.system.entity.RoleEntity;
import com.huicai.base.system.service.MenuService;
import com.huicai.base.system.service.RoleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class RoleControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private RoleService roleService;

    @MockBean
    private MenuService menuService;

    @Test
    @DisplayName("分页查询角色_默认参数正确生效")
    void page_defaultParams_applied() throws Exception {
        IPage<RoleEntity> page = new Page<>(1, 10);
        when(roleService.pageRole(anyLong(), anyLong(), isNull(), isNull())).thenReturn(page);

        mvc.perform(get("/api/v1/system/role/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("分页查询角色_自定义参数正确绑定")
    void page_customParams_boundCorrectly() throws Exception {
        IPage<RoleEntity> page = new Page<>(2, 20);
        when(roleService.pageRole(eq(2L), eq(20L), eq("admin"), eq("ACTIVE"))).thenReturn(page);

        mvc.perform(get("/api/v1/system/role/page")
                        .param("page", "2")
                        .param("size", "20")
                        .param("keyword", "admin")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("获取角色详情_存在时返回200")
    void getById_exists_returnsOk() throws Exception {
        RoleEntity role = new RoleEntity();
        role.setId(1L);
        role.setName("管理员");
        role.setCode("ADMIN");
        when(roleService.getById(eq(1L))).thenReturn(role);

        mvc.perform(get("/api/v1/system/role/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("管理员"));
    }

    @Test
    @DisplayName("获取角色详情_不存在时返回200和null")
    void getById_notExists_returnsNull() throws Exception {
        when(roleService.getById(eq(999L))).thenReturn(null);

        mvc.perform(get("/api/v1/system/role/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("获取角色菜单ID列表_返回200")
    void getMenus_returnsOk() throws Exception {
        when(menuService.getMenuIdsByRoleId(eq(1L))).thenReturn(List.of(1L, 2L, 3L));

        mvc.perform(get("/api/v1/system/role/1/menus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("分配角色菜单_RequestBody正确解析_返回200")
    void assignMenus_requestBodyParsed_returnsOk() throws Exception {
        Map<String, List<Long>> body = Map.of("menuIds", List.of(1L, 2L, 3L));
        doNothing().when(roleService).assignMenus(anyLong(), anyList());

        mvc.perform(put("/api/v1/system/role/1/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(roleService).assignMenus(eq(1L), argThat(ids -> ids.size() == 3));
    }

    @Test
    @DisplayName("新增角色_RequestBody正确解析_返回200")
    void create_requestBodyParsed_returnsOk() throws Exception {
        RoleEntity role = new RoleEntity();
        role.setName("审计员");
        role.setCode("AUDITOR");

        doNothing().when(roleService).create(any());

        mvc.perform(post("/api/v1/system/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(roleService).create(argThat(r -> "审计员".equals(r.getName())));
    }

    @Test
    @DisplayName("修改角色_PathVariable+RequestBody正确绑定")
    void update_pathVariableAndBody_boundCorrectly() throws Exception {
        RoleEntity role = new RoleEntity();
        role.setName("超级管理员");

        doNothing().when(roleService).update(any());

        mvc.perform(put("/api/v1/system/role/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(roleService).update(argThat(r -> r.getId() == 1L && "超级管理员".equals(r.getName())));
    }

    @Test
    @DisplayName("修改角色状态_RequestBody正确解析_返回200")
    void updateStatus_requestBodyParsed_returnsOk() throws Exception {
        Map<String, String> body = Map.of("status", "DISABLED");
        doNothing().when(roleService).updateStatus(anyLong(), anyString());

        mvc.perform(put("/api/v1/system/role/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(roleService).updateStatus(eq(1L), eq("DISABLED"));
    }

    @Test
    @DisplayName("删除角色_PathVariable正确解析_返回200")
    void delete_pathVariable_boundCorrectly() throws Exception {
        doNothing().when(roleService).delete(anyLong());

        mvc.perform(delete("/api/v1/system/role/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(roleService).delete(eq(1L));
    }
}