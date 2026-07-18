package com.huicai.module.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.module.arap.entity.CustomerEntity;
import com.huicai.module.arap.service.CustomerService;
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
class CustomerControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private CustomerService customerService;

    @Test
    @DisplayName("分页查询客户_默认参数正确生效")
    void page_defaultParams_applied() throws Exception {
        IPage<CustomerEntity> page = new Page<>(1, 20);
        when(customerService.pageQuery(isNull(), isNull(), eq(1), eq(20))).thenReturn(page);

        mvc.perform(get("/api/v1/customers/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("分页查询客户_自定义参数正确绑定")
    void page_customParams_boundCorrectly() throws Exception {
        IPage<CustomerEntity> page = new Page<>(2, 10);
        when(customerService.pageQuery(eq("华为"), eq(true), eq(2), eq(10))).thenReturn(page);

        mvc.perform(get("/api/v1/customers/page")
                        .param("keyword", "华为")
                        .param("isActive", "true")
                        .param("current", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("获取客户详情_存在时返回200")
    void getById_exists_returnsOk() throws Exception {
        CustomerEntity customer = new CustomerEntity();
        customer.setId(1L);
        customer.setName("华为技术有限公司");
        customer.setCode("CUST001");
        customer.setContactPerson("张三");
        when(customerService.getById(eq(1L))).thenReturn(customer);

        mvc.perform(get("/api/v1/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("华为技术有限公司"))
                .andExpect(jsonPath("$.data.code").value("CUST001"));
    }

    @Test
    @DisplayName("获取客户详情_不存在时返回200和null")
    void getById_notExists_returnsNull() throws Exception {
        when(customerService.getById(eq(999L))).thenReturn(null);

        mvc.perform(get("/api/v1/customers/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("查询全部客户_返回200")
    void listAll_returnsOk() throws Exception {
        CustomerEntity cust = new CustomerEntity();
        cust.setId(1L);
        cust.setName("客户A");
        when(customerService.listAll()).thenReturn(List.of(cust));

        mvc.perform(get("/api/v1/customers/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("客户A"));
    }
}