package com.huicai.sme.cash.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.sme.cash.service.BankReconciliationService;
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
 * 银行对账 Controller 层测试.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class BankReconciliationControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private BankReconciliationService service;

    // ==================== 查询接口测试 ====================

    @Test
    @DisplayName("余额调节表_参数正确绑定")
    void adjustment_paramsBoundCorrectly() throws Exception {
        // given
        when(service.generateAdjustment(eq(1L), eq("202606")))
                .thenReturn(Map.of("balance", new BigDecimal("10000.00")));

        // when & then
        mvc.perform(get("/api/sme/cash/v1/bank-reconciliation/adjustment")
                        .param("accountId", "1")
                        .param("period", "202606"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(10000.00));

        verify(service).generateAdjustment(eq(1L), eq("202606"));
    }

    @Test
    @DisplayName("对账汇总_参数正确绑定")
    void summary_paramsBoundCorrectly() throws Exception {
        // given
        when(service.summarize(eq(1L), eq("202606")))
                .thenReturn(Map.of("total", 100, "matched", 80));

        // when & then
        mvc.perform(get("/api/sme/cash/v1/bank-reconciliation/summary")
                        .param("accountId", "1")
                        .param("period", "202606"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(100))
                .andExpect(jsonPath("$.data.matched").value(80));

        verify(service).summarize(eq(1L), eq("202606"));
    }

    @Test
    @DisplayName("未达账项_参数正确绑定")
    void unmatched_paramsBoundCorrectly() throws Exception {
        // given
        when(service.unmatchedItems(eq(1L), eq("202606")))
                .thenReturn(List.of());

        // when & then
        mvc.perform(get("/api/sme/cash/v1/bank-reconciliation/unmatched")
                        .param("accountId", "1")
                        .param("period", "202606"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(service).unmatchedItems(eq(1L), eq("202606"));
    }

    @Test
    @DisplayName("单笔5维评分_参数正确绑定")
    void score_paramsBoundCorrectly() throws Exception {
        // given - 返回 null 验证参数传递即可
        when(service.calculateScore(eq(1L), eq(100L), eq(200L))).thenReturn(null);

        // when & then
        mvc.perform(get("/api/sme/cash/v1/bank-reconciliation/score")
                        .param("accountId", "1")
                        .param("statementId", "100")
                        .param("journalId", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(service).calculateScore(eq(1L), eq(100L), eq(200L));
    }

    // ==================== 操作接口测试 ====================

    @Test
    @DisplayName("批量自动匹配_参数正确绑定")
    void runMatching_paramsBoundCorrectly() throws Exception {
        // given - 返回 null 验证参数传递即可
        when(service.runMatching(eq(1L), eq("202606"))).thenReturn(null);

        // when & then
        mvc.perform(post("/api/sme/cash/v1/bank-reconciliation/run-matching")
                        .param("accountId", "1")
                        .param("period", "202606")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(service).runMatching(eq(1L), eq("202606"));
    }

    @Test
    @DisplayName("获取对账锁_全部参数正确绑定")
    void lock_allParamsBoundCorrectly() throws Exception {
        // given
        when(service.lockReconciliation(eq(1L), eq("202606"), eq("admin"), eq(300L)))
                .thenReturn(true);

        // when & then
        mvc.perform(post("/api/sme/cash/v1/bank-reconciliation/lock")
                        .param("accountId", "1")
                        .param("period", "202606")
                        .param("operator", "admin")
                        .param("ttlSeconds", "300")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        verify(service).lockReconciliation(eq(1L), eq("202606"), eq("admin"), eq(300L));
    }

    @Test
    @DisplayName("获取对账锁_ttl使用默认值")
    void lock_ttlUsesDefault() throws Exception {
        // given
        when(service.lockReconciliation(eq(1L), eq("202606"), eq("admin"), eq(300L)))
                .thenReturn(true);

        // when & then - 不传 ttlSeconds，使用默认值 300
        mvc.perform(post("/api/sme/cash/v1/bank-reconciliation/lock")
                        .param("accountId", "1")
                        .param("period", "202606")
                        .param("operator", "admin")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk());

        verify(service).lockReconciliation(eq(1L), eq("202606"), eq("admin"), eq(300L));
    }

    @Test
    @DisplayName("释放对账锁_参数正确绑定")
    void unlock_paramsBoundCorrectly() throws Exception {
        // given
        doNothing().when(service).unlockReconciliation(anyLong(), anyString(), anyString());

        // when & then
        mvc.perform(post("/api/sme/cash/v1/bank-reconciliation/unlock")
                        .param("accountId", "1")
                        .param("period", "202606")
                        .param("operator", "admin")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk());

        verify(service).unlockReconciliation(eq(1L), eq("202606"), eq("admin"));
    }

    @Test
    @DisplayName("确认匹配_operator使用默认值")
    void confirm_operatorUsesDefault() throws Exception {
        // given - 返回 null 验证参数传递即可
        when(service.confirmMatch(eq(1L), eq(100L), eq("system"))).thenReturn(null);

        // when & then - 不传 operator，使用默认值 "system"
        mvc.perform(post("/api/sme/cash/v1/bank-reconciliation/confirm")
                        .param("statementId", "1")
                        .param("journalId", "100")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(service).confirmMatch(eq(1L), eq(100L), eq("system"));
    }

    @Test
    @DisplayName("确认匹配_operator参数正确传递")
    void confirm_operatorParamPassedCorrectly() throws Exception {
        // given - 返回 null 验证参数传递即可
        when(service.confirmMatch(eq(1L), eq(100L), eq("tester"))).thenReturn(null);

        // when & then
        mvc.perform(post("/api/sme/cash/v1/bank-reconciliation/confirm")
                        .param("statementId", "1")
                        .param("journalId", "100")
                        .param("operator", "tester")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(service).confirmMatch(eq(1L), eq(100L), eq("tester"));
    }

    @Test
    @DisplayName("驳回匹配_operator使用默认值")
    void reject_operatorUsesDefault() throws Exception {
        // given - 返回 null 验证参数传递即可
        when(service.rejectMatch(eq(1L), eq(100L), eq("system"))).thenReturn(null);

        // when & then - 不传 operator，使用默认值 "system"
        mvc.perform(post("/api/sme/cash/v1/bank-reconciliation/reject")
                        .param("statementId", "1")
                        .param("journalId", "100")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk());

        verify(service).rejectMatch(eq(1L), eq(100L), eq("system"));
    }

    @Test
    @DisplayName("驳回匹配_operator参数正确传递")
    void reject_operatorParamPassedCorrectly() throws Exception {
        // given - 返回 null 验证参数传递即可
        when(service.rejectMatch(eq(1L), eq(100L), eq("tester"))).thenReturn(null);

        // when & then
        mvc.perform(post("/api/sme/cash/v1/bank-reconciliation/reject")
                        .param("statementId", "1")
                        .param("journalId", "100")
                        .param("operator", "tester")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk());

        verify(service).rejectMatch(eq(1L), eq(100L), eq("tester"));
    }
}
