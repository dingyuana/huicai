package com.huicai.module.arap.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.module.arap.dto.ReceivableVO;
import com.huicai.module.arap.entity.ReceivableEntity;
import com.huicai.module.arap.service.ReceivableService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 应收单 Controller 层测试.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class ReceivableControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private ReceivableService service;

    // ==================== 查询接口测试 ====================

    @Test
    @DisplayName("分页查询_默认参数正确传递")
    void page_defaultParams_passedCorrectly() throws Exception {
        // given
        IPage<ReceivableVO> page = new Page<>(1, 20);
        when(service.pageQuery(isNull(), isNull(), isNull(), isNull(), isNull(), eq(1), eq(20))).thenReturn(page);

        // when & then
        mvc.perform(get("/api/v1/receivables/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(service).pageQuery(isNull(), isNull(), isNull(), isNull(), isNull(), eq(1), eq(20));
    }

    @Test
    @DisplayName("分页查询_全部参数正确绑定")
    void page_allParams_boundCorrectly() throws Exception {
        // given
        IPage<ReceivableVO> page = new Page<>(2, 50);
        when(service.pageQuery(eq(10L), eq("202606"), isNull(), isNull(), isNull(), eq(2), eq(50))).thenReturn(page);

        // when & then
        mvc.perform(get("/api/v1/receivables/page")
                        .param("customerId", "10")
                        .param("period", "202606")
                        .param("current", "2")
                        .param("size", "50"))
                .andExpect(status().isOk());

        verify(service).pageQuery(eq(10L), eq("202606"), isNull(), isNull(), isNull(), eq(2), eq(50));
    }

    @Test
    @DisplayName("详情查询_PathVariable正确解析")
    void getById_pathVariable_boundCorrectly() throws Exception {
        // given
        ReceivableVO vo = new ReceivableVO();
        vo.setId(123L);
        vo.setAmount(new BigDecimal("5000.00"));
        when(service.getById(eq(123L))).thenReturn(vo);

        // when & then
        mvc.perform(get("/api/v1/receivables/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(123))
                .andExpect(jsonPath("$.data.amount").value(5000.00));

        verify(service).getById(eq(123L));
    }

    // ==================== 创建接口测试 ====================

    @Test
    @DisplayName("创建应收单_RequestBody正确解析")
    void create_requestBody_parsedCorrectly() throws Exception {
        // given
        ReceivableEntity entity = new ReceivableEntity();
        entity.setCustomerId(10L);
        entity.setAmount(new BigDecimal("8000.00"));

        ReceivableEntity result = new ReceivableEntity();
        result.setId(100L);
        when(service.create(any())).thenReturn(result);

        // when & then
        mvc.perform(post("/api/v1/receivables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(entity)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100));

        verify(service).create(argThat(e -> e.getCustomerId() == 10L));
    }

    // ==================== 状态转换接口测试 ====================

    @Test
    @DisplayName("确认应收单_PathVariable正确传递")
    void confirm_pathVariable_passedCorrectly() throws Exception {
        // given
        doNothing().when(service).confirm(anyLong(), anyLong());

        // when & then
        mvc.perform(post("/api/v1/receivables/50/confirm"))
                .andExpect(status().isOk());

        verify(service).confirm(eq(50L), eq(0L));
    }

    @Test
    @DisplayName("冲销应收单_PathVariable正确传递")
    void reverse_pathVariable_passedCorrectly() throws Exception {
        // given
        doNothing().when(service).reverse(anyLong(), anyLong());

        // when & then
        mvc.perform(post("/api/v1/receivables/50/reverse"))
                .andExpect(status().isOk());

        verify(service).reverse(eq(50L), eq(0L));
    }

    // ==================== 分析接口测试 ====================

    @Test
    @DisplayName("逾期应收列表_Service正确调用")
    void overdue_serviceCalled() throws Exception {
        // given
        when(service.overdueList()).thenReturn(List.of());

        // when & then
        mvc.perform(get("/api/v1/receivables/overdue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(service).overdueList();
    }

    @Test
    @DisplayName("账龄分析_不带客户ID_调用总体分析")
    void aging_noCustomerId_overallAgingCalled() throws Exception {
        // given
        when(service.overallAging()).thenReturn(Map.of("total", 100));

        // when & then
        mvc.perform(get("/api/v1/receivables/aging"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(100));

        verify(service).overallAging();
    }

    @Test
    @DisplayName("账龄分析_带客户ID_调用客户分析")
    void aging_withCustomerId_analysisCalled() throws Exception {
        // given
        when(service.agingAnalysis(eq(10L))).thenReturn(Map.of("customerId", 10));

        // when & then
        mvc.perform(get("/api/v1/receivables/aging")
                        .param("customerId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerId").value(10));

        verify(service).agingAnalysis(eq(10L));
    }
}
