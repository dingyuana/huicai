package com.huicai.base.system.controller;

import com.huicai.base.system.entity.MenuEntity;
import com.huicai.base.system.service.MenuService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class MenuControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private MenuService menuService;

    @Test
    @DisplayName("获取菜单树_返回200和树形结构")
    void tree_returnsTree() throws Exception {
        when(menuService.getMenuTree()).thenReturn(List.of());

        mvc.perform(get("/api/v1/system/menu/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("获取菜单选项_返回200")
    void options_returnsOptions() throws Exception {
        when(menuService.getMenuOptions()).thenReturn(List.of());

        mvc.perform(get("/api/v1/system/menu/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("获取菜单详情_存在时返回200")
    void getById_exists_returnsOk() throws Exception {
        MenuEntity menu = new MenuEntity();
        menu.setId(1L);
        menu.setName("用户管理");
        when(menuService.getById(eq(1L))).thenReturn(menu);

        mvc.perform(get("/api/v1/system/menu/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("用户管理"));
    }

    @Test
    @DisplayName("获取菜单详情_不存在时返回200和null")
    void getById_notExists_returnsNull() throws Exception {
        when(menuService.getById(eq(999L))).thenReturn(null);

        mvc.perform(get("/api/v1/system/menu/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("新增菜单_RequestBody正确解析_返回200")
    void create_requestBodyParsed_returnsOk() throws Exception {
        MenuEntity menu = new MenuEntity();
        menu.setName("系统管理");
        menu.setType("MENU");
        menu.setPath("/system");

        doNothing().when(menuService).create(any());

        mvc.perform(post("/api/v1/system/menu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(menu)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(menuService).create(argThat(m -> "系统管理".equals(m.getName())));
    }

    @Test
    @DisplayName("修改菜单_PathVariable+RequestBody正确绑定")
    void update_pathVariableAndBody_boundCorrectly() throws Exception {
        MenuEntity menu = new MenuEntity();
        menu.setName("角色管理");

        doNothing().when(menuService).update(any());

        mvc.perform(put("/api/v1/system/menu/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(menu)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(menuService).update(argThat(m -> m.getId() == 2L && "角色管理".equals(m.getName())));
    }

    @Test
    @DisplayName("删除菜单_PathVariable正确解析_返回200")
    void delete_pathVariable_boundCorrectly() throws Exception {
        doNothing().when(menuService).delete(anyLong());

        mvc.perform(delete("/api/v1/system/menu/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(menuService).delete(eq(3L));
    }
}