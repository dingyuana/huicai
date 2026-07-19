package com.huicai.base.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.base.masterdata.entity.EmployeeEntity;
import com.huicai.base.masterdata.service.EmployeeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class EmployeeControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private EmployeeService employeeService;

    @Test
    @DisplayName("获取在职员工列表_返回200和员工列表")
    void list_returnsEmployeeList() throws Exception {
        EmployeeEntity emp = new EmployeeEntity();
        emp.setId(1L);
        emp.setName("张三");
        emp.setCode("EMP001");
        emp.setPhone("13800138001");
        when(employeeService.listAll()).thenReturn(List.of(emp));

        mvc.perform(get("/api/v1/employees/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("张三"))
                .andExpect(jsonPath("$.data[0].code").value("EMP001"));
    }

    @Test
    @DisplayName("分页查询员工_默认参数正确生效")
    void page_defaultParams_applied() throws Exception {
        IPage<EmployeeEntity> page = new Page<>(1, 20);
        when(employeeService.pageQuery(isNull(), isNull(), eq(1), eq(20))).thenReturn(page);

        mvc.perform(get("/api/v1/employees/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("分页查询员工_自定义参数正确绑定")
    void page_customParams_boundCorrectly() throws Exception {
        IPage<EmployeeEntity> page = new Page<>(2, 10);
        when(employeeService.pageQuery(eq("张三"), eq(true), eq(2), eq(10))).thenReturn(page);

        mvc.perform(get("/api/v1/employees/page")
                        .param("keyword", "张三")
                        .param("isActive", "true")
                        .param("current", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("获取员工详情_存在时返回200")
    void getById_exists_returnsOk() throws Exception {
        EmployeeEntity emp = new EmployeeEntity();
        emp.setId(1L);
        emp.setName("李四");
        emp.setCode("EMP002");
        emp.setPhone("13800138002");
        when(employeeService.getById(eq(1L))).thenReturn(emp);

        mvc.perform(get("/api/v1/employees/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("李四"))
                .andExpect(jsonPath("$.data.code").value("EMP002"));
    }

    @Test
    @DisplayName("获取员工详情_不存在时Service返回null_接口返回200和null")
    void getById_notExists_returnsNull() throws Exception {
        when(employeeService.getById(eq(999L))).thenReturn(null);

        mvc.perform(get("/api/v1/employees/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("按姓名查询员工_返回200")
    void byName_returnsOk() throws Exception {
        EmployeeEntity emp = new EmployeeEntity();
        emp.setId(1L);
        emp.setName("张三");
        when(employeeService.findByName(eq("张三"))).thenReturn(emp);

        mvc.perform(get("/api/v1/employees/by-name")
                        .param("name", "张三"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("张三"));
    }
}