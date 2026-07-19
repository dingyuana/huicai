package com.huicai.base.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.base.masterdata.entity.VendorEntity;
import com.huicai.base.masterdata.service.VendorService;
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
class VendorControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private VendorService vendorService;

    @Test
    @DisplayName("分页查询供应商_默认参数正确生效")
    void page_defaultParams_applied() throws Exception {
        IPage<VendorEntity> page = new Page<>(1, 20);
        when(vendorService.pageQuery(isNull(), isNull(), eq(1), eq(20))).thenReturn(page);

        mvc.perform(get("/api/v1/vendors/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("分页查询供应商_自定义参数正确绑定")
    void page_customParams_boundCorrectly() throws Exception {
        IPage<VendorEntity> page = new Page<>(2, 10);
        when(vendorService.pageQuery(eq("供应商A"), eq(true), eq(2), eq(10))).thenReturn(page);

        mvc.perform(get("/api/v1/vendors/page")
                        .param("keyword", "供应商A")
                        .param("isActive", "true")
                        .param("current", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("获取供应商详情_存在时返回200")
    void getById_exists_returnsOk() throws Exception {
        VendorEntity vendor = new VendorEntity();
        vendor.setId(1L);
        vendor.setName("华为技术有限公司");
        vendor.setCode("VEN001");
        vendor.setContactPerson("李四");
        when(vendorService.getById(eq(1L))).thenReturn(vendor);

        mvc.perform(get("/api/v1/vendors/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("华为技术有限公司"))
                .andExpect(jsonPath("$.data.code").value("VEN001"));
    }

    @Test
    @DisplayName("获取供应商详情_不存在时返回200和null")
    void getById_notExists_returnsNull() throws Exception {
        when(vendorService.getById(eq(999L))).thenReturn(null);

        mvc.perform(get("/api/v1/vendors/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("查询全部供应商_返回200")
    void listAll_returnsOk() throws Exception {
        VendorEntity vendor = new VendorEntity();
        vendor.setId(1L);
        vendor.setName("供应商A");
        when(vendorService.listAll()).thenReturn(List.of(vendor));

        mvc.perform(get("/api/v1/vendors/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("供应商A"));
    }
}