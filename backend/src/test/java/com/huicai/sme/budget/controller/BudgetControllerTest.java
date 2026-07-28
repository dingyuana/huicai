package com.huicai.sme.budget.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.sme.budget.entity.BudgetEntity;
import com.huicai.sme.budget.service.BudgetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class BudgetControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private BudgetService service;

    @Test
    @DisplayName("分页查询预算_参数正确绑定")
    void pageQuery_params_applied() throws Exception {
        when(service.pageQuery(any(), any(), eq(1), eq(20))).thenReturn(new Page<>());

        mvc.perform(get("/api/sme/budget/v1/budgets/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("查询预算详情_PathVariable正确解析")
    void getById_pathVariable_parsedCorrectly() throws Exception {
        BudgetEntity entity = new BudgetEntity();
        entity.setId(1L);
        when(service.getById(eq(1L))).thenReturn(entity);

        mvc.perform(get("/api/sme/budget/v1/budgets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("提交预算_状态流转")
    void submit_statusTransition() throws Exception {
        BudgetEntity entity = new BudgetEntity();
        entity.setId(1L);
        entity.setStatus("SUBMITTED");
        when(service.submit(eq(1L))).thenReturn(entity);

        mvc.perform(post("/api/sme/budget/v1/budgets/1/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    @Test
    @DisplayName("审批预算_审批端点")
    void approve_approveEndpoint() throws Exception {
        BudgetEntity entity = new BudgetEntity();
        entity.setId(1L);
        entity.setStatus("APPROVED");
        when(service.approve(eq(1L))).thenReturn(entity);

        mvc.perform(post("/api/sme/budget/v1/budgets/1/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("激活预算_激活端点")
    void activate_activateEndpoint() throws Exception {
        BudgetEntity entity = new BudgetEntity();
        entity.setId(1L);
        entity.setStatus("ACTIVE");
        when(service.activate(eq(1L))).thenReturn(entity);

        mvc.perform(post("/api/sme/budget/v1/budgets/1/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("预算检查_RequestParam正确绑定")
    void checkBudget_params_boundCorrectly() throws Exception {
        when(service.checkBudget(anyLong(), anyString(), any())).thenReturn(Map.of("warn", false));

        mvc.perform(get("/api/sme/budget/v1/budgets/check")
                        .param("subjectId", "1001")
                        .param("period", "202601")
                        .param("amount", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("执行分析_RequestParam正确绑定")
    void execution_params_boundCorrectly() throws Exception {
        when(service.executionAnalysis(eq("202601"))).thenReturn(Map.of("rate", 0.75));

        mvc.perform(get("/api/sme/budget/v1/budgets/execution")
                        .param("period", "202601"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}