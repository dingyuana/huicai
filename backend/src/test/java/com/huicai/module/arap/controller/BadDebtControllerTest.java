package com.huicai.module.arap.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.module.arap.entity.BadDebtProvisionEntity;
import com.huicai.module.arap.service.BadDebtService;
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

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class BadDebtControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private BadDebtService service;

    @Test
    @DisplayName("分页查询坏账_默认参数正确生效")
    void page_defaultParams_applied() throws Exception {
        IPage<BadDebtProvisionEntity> page = new Page<>(1, 20);
        when(service.pageQuery(isNull(), eq(1), eq(20))).thenReturn(page);

        mvc.perform(get("/api/v1/bad-debts/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("分页查询坏账_自定义参数正确绑定")
    void page_customParams_boundCorrectly() throws Exception {
        IPage<BadDebtProvisionEntity> page = new Page<>(2, 50);
        when(service.pageQuery(eq("CONFIRMED"), eq(2), eq(50))).thenReturn(page);

        mvc.perform(get("/api/v1/bad-debts/page")
                        .param("status", "CONFIRMED")
                        .param("current", "2")
                        .param("size", "50"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("坏账详情_PathVariable正确解析")
    void getById_pathVariable_parsedCorrectly() throws Exception {
        BadDebtProvisionEntity entity = new BadDebtProvisionEntity();
        entity.setId(1L);
        entity.setStatus("DRAFT");
        entity.setPeriod("2024-01");
        entity.setTotalAmount(new BigDecimal("50000.00"));
        when(service.getById(eq(1L))).thenReturn(entity);

        mvc.perform(get("/api/v1/bad-debts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.period").value("2024-01"));
    }

    @Test
    @DisplayName("账龄比例法计提_RequestParam+RequestBody正确绑定")
    void provisionByAging_params_boundCorrectly() throws Exception {
        BadDebtProvisionEntity entity = new BadDebtProvisionEntity();
        entity.setId(1L);
        entity.setMethod("AGING");
        when(service.provisionByAging(anyString(), any())).thenReturn(entity);

        Map<String, BigDecimal> ratios = Map.of("0-30", new BigDecimal("0.01"), "31-60", new BigDecimal("0.05"));

        mvc.perform(post("/api/v1/bad-debts/provision/aging")
                        .param("period", "2024-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(ratios)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("AGING"));

        verify(service).provisionByAging(eq("2024-01"), argThat(m -> m.containsKey("0-30") && m.containsKey("31-60")));
    }

    @Test
    @DisplayName("余额百分比法计提_RequestParam正确绑定")
    void provisionByPercentage_params_boundCorrectly() throws Exception {
        BadDebtProvisionEntity entity = new BadDebtProvisionEntity();
        entity.setId(1L);
        entity.setMethod("PERCENTAGE");
        when(service.provisionByPercentage(anyString(), any())).thenReturn(entity);

        mvc.perform(post("/api/v1/bad-debts/provision/percentage")
                        .param("period", "2024-01")
                        .param("ratio", "0.05")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("PERCENTAGE"));

        verify(service).provisionByPercentage(eq("2024-01"), eq(new BigDecimal("0.05")));
    }

    @Test
    @DisplayName("确认坏账_PathVariable正确绑定")
    void confirm_pathVariable_boundCorrectly() throws Exception {
        BadDebtProvisionEntity entity = new BadDebtProvisionEntity();
        entity.setId(1L);
        entity.setStatus("VOUCHERED");
        when(service.confirm(eq(1L), anyLong())).thenReturn(entity);

        mvc.perform(post("/api/v1/bad-debts/1/confirm")
                        .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VOUCHERED"));

        verify(service).confirm(eq(1L), eq(1L));
    }

    @Test
    @DisplayName("删除坏账_PathVariable正确绑定")
    void delete_pathVariable_boundCorrectly() throws Exception {
        doNothing().when(service).delete(anyLong());

        mvc.perform(delete("/api/v1/bad-debts/1"))
                .andExpect(status().isOk());

        verify(service).delete(eq(1L));
    }
}