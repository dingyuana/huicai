package com.huicai.module.system.controller;

import com.huicai.module.system.entity.DeptEntity;
import com.huicai.module.system.service.DeptService;
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
class DeptControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private DeptService deptService;

    @Test
    @DisplayName("获取部门树_返回200和树形结构")
    void tree_returnsTree() throws Exception {
        when(deptService.getDeptTree()).thenReturn(List.of());

        mvc.perform(get("/api/v1/system/dept/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("获取部门详情_存在时返回200")
    void getById_exists_returnsOk() throws Exception {
        DeptEntity dept = new DeptEntity();
        dept.setId(1L);
        dept.setName("财务部");
        when(deptService.getById(eq(1L))).thenReturn(dept);

        mvc.perform(get("/api/v1/system/dept/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("财务部"));
    }

    @Test
    @DisplayName("获取部门详情_不存在时Service返回null_接口返回200和null")
    void getById_notExists_returnsNull() throws Exception {
        when(deptService.getById(eq(999L))).thenReturn(null);

        mvc.perform(get("/api/v1/system/dept/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("新增部门_RequestBody正确解析_返回200")
    void create_requestBodyParsed_returnsOk() throws Exception {
        DeptEntity dept = new DeptEntity();
        dept.setName("技术部");
        dept.setParentId(0L);

        doNothing().when(deptService).create(any());

        mvc.perform(post("/api/v1/system/dept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(dept)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(deptService).create(argThat(d -> "技术部".equals(d.getName())));
    }

    @Test
    @DisplayName("修改部门_PathVariable+RequestBody正确绑定")
    void update_pathVariableAndBody_boundCorrectly() throws Exception {
        DeptEntity dept = new DeptEntity();
        dept.setName("研发部");

        doNothing().when(deptService).update(any());

        mvc.perform(put("/api/v1/system/dept/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(dept)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(deptService).update(argThat(d -> d.getId() == 1L && "研发部".equals(d.getName())));
    }

    @Test
    @DisplayName("删除部门_PathVariable正确解析_返回200")
    void delete_pathVariable_boundCorrectly() throws Exception {
        doNothing().when(deptService).delete(anyLong());

        mvc.perform(delete("/api/v1/system/dept/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(deptService).delete(eq(1L));
    }
}