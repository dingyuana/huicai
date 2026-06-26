package com.huicai.module.arap.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.module.arap.dto.PayableVO;
import com.huicai.module.arap.entity.PayableEntity;
import com.huicai.module.arap.service.PayableService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 应付单 Controller 层测试.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class PayableControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private PayableService service;

    // ==================== 查询接口测试 ====================

    @Test
    @DisplayName("分页查询_默认参数正确传递")
    void page_defaultParams_passedCorrectly() throws Exception {
        // given
        IPage<PayableVO> page = new Page<>(1, 20);
        when(service.pageQuery(isNull(), isNull(), eq(1), eq(20))).thenReturn(page);

        // when & then
        mvc.perform(get("/api/v1/payables/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(service).pageQuery(isNull(), isNull(), eq(1), eq(20));
    }

    @Test
    @DisplayName("分页查询_全部参数正确绑定")
    void page_allParams_boundCorrectly() throws Exception {
        // given
        IPage<PayableVO> page = new Page<>(2, 50);
        when(service.pageQuery(eq(20L), eq("202606"), eq(2), eq(50))).thenReturn(page);

        // when & then
        mvc.perform(get("/api/v1/payables/page")
                        .param("vendorId", "20")
                        .param("period", "202606")
                        .param("current", "2")
                        .param("size", "50"))
                .andExpect(status().isOk());

        verify(service).pageQuery(eq(20L), eq("202606"), eq(2), eq(50));
    }

    @Test
    @DisplayName("详情查询_PathVariable正确解析")
    void getById_pathVariable_boundCorrectly() throws Exception {
        // given
        PayableVO vo = new PayableVO();
        vo.setId(456L);
        vo.setAmount(new BigDecimal("3000.00"));
        when(service.getById(eq(456L))).thenReturn(vo);

        // when & then
        mvc.perform(get("/api/v1/payables/456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(456))
                .andExpect(jsonPath("$.data.amount").value(3000.00));

        verify(service).getById(eq(456L));
    }

    // ==================== 创建接口测试 ====================

    @Test
    @DisplayName("创建应付单_RequestBody正确解析")
    void create_requestBody_parsedCorrectly() throws Exception {
        // given
        PayableEntity entity = new PayableEntity();
        entity.setVendorId(20L);
        entity.setAmount(new BigDecimal("6000.00"));

        PayableEntity result = new PayableEntity();
        result.setId(200L);
        when(service.create(any())).thenReturn(result);

        // when & then
        mvc.perform(post("/api/v1/payables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(entity)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(200));

        verify(service).create(argThat(e -> e.getVendorId() == 20L));
    }

    // ==================== 状态转换接口测试 ====================

    @Test
    @DisplayName("确认应付单_PathVariable正确传递")
    void confirm_pathVariable_passedCorrectly() throws Exception {
        // given
        doNothing().when(service).confirm(anyLong(), anyLong());

        // when & then
        mvc.perform(post("/api/v1/payables/60/confirm"))
                .andExpect(status().isOk());

        verify(service).confirm(eq(60L), eq(0L));
    }

    @Test
    @DisplayName("冲销应付单_PathVariable正确传递")
    void reverse_pathVariable_passedCorrectly() throws Exception {
        // given
        doNothing().when(service).reverse(anyLong(), anyLong());

        // when & then
        mvc.perform(post("/api/v1/payables/60/reverse"))
                .andExpect(status().isOk());

        verify(service).reverse(eq(60L), eq(0L));
    }

    // ==================== 分析接口测试 ====================

    @Test
    @DisplayName("账龄分析_不带供应商ID_返回提示")
    void aging_noVendorId_returnsHint() throws Exception {
        // when & then
        mvc.perform(get("/api/v1/payables/aging"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("请指定供应商ID"));

        verify(service, never()).agingAnalysis(anyLong());
    }

    @Test
    @DisplayName("账龄分析_带供应商ID_调用分析方法")
    void aging_withVendorId_analysisCalled() throws Exception {
        // given
        when(service.agingAnalysis(eq(20L))).thenReturn(Map.of("vendorId", 20));

        // when & then
        mvc.perform(get("/api/v1/payables/aging")
                        .param("vendorId", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vendorId").value(20));

        verify(service).agingAnalysis(eq(20L));
    }
}
