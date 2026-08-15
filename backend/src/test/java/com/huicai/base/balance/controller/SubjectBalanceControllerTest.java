package com.huicai.base.balance.controller;

import com.huicai.base.balance.dto.SubjectBalanceVO;
import com.huicai.base.balance.service.SubjectBalanceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class SubjectBalanceControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private SubjectBalanceService subjectBalanceService;

    @Test
    @DisplayName("按期间查询科目余额_返回200和余额列表(含科目编码/名称/方向)")
    void listByPeriod_returnsBalanceList() throws Exception {
        SubjectBalanceVO balance = new SubjectBalanceVO();
        balance.setId(1L);
        balance.setSubjectId(1001L);
        balance.setSubjectCode("1001");
        balance.setSubjectName("库存现金");
        balance.setDirection("debit");
        balance.setPeriod("202607");
        balance.setBeginBalance(BigDecimal.ZERO);
        balance.setDebitTotal(new BigDecimal("1000"));
        balance.setCreditTotal(new BigDecimal("500"));
        balance.setEndBalance(new BigDecimal("500"));

        when(subjectBalanceService.queryByPeriodWithSubject(eq("202607"))).thenReturn(List.of(balance));

        mvc.perform(get("/api/base/balance/v1/subject-balances")
                        .param("period", "202607"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].period").value("202607"))
                .andExpect(jsonPath("$.data[0].subjectCode").value("1001"))
                .andExpect(jsonPath("$.data[0].subjectName").value("库存现金"))
                .andExpect(jsonPath("$.data[0].direction").value("debit"))
                .andExpect(jsonPath("$.data[0].endBalance").value(500));
    }

    @Test
    @DisplayName("查询科目余额_service返回空列表_接口返回200和空列表")
    void listByPeriod_emptyResult_returnsEmptyList() throws Exception {
        when(subjectBalanceService.queryByPeriodWithSubject(eq("202608"))).thenReturn(List.of());

        mvc.perform(get("/api/base/balance/v1/subject-balances")
                        .param("period", "202608"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("试算平衡检查_返回200和结果")
    void trialBalance_returnsMap() throws Exception {
        Map<String, Object> result = Map.of(
                "totalDebit", new BigDecimal("10000"),
                "totalCredit", new BigDecimal("10000"),
                "balanced", true
        );

        when(subjectBalanceService.checkTrialBalance(eq("202607"))).thenReturn(result);

        mvc.perform(get("/api/base/balance/v1/subject-balances/trial-balance")
                        .param("period", "202607"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.balanced").value(true));
    }

    @Test
    @DisplayName("期初建账_参数正确绑定_返回200")
    void initOpening_paramsBoundCorrectly() throws Exception {
        doNothing().when(subjectBalanceService).initOpeningBalances(anyString(), any(), anyMap());

        mvc.perform(post("/api/base/balance/v1/subject-balances/init")
                        .param("period", "202607")
                        .param("openedAt", "2026-07-01 00:00:00")
                        .contentType("application/json")
                        .content("""
                                {"1001": 1000, "1002": 2000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(subjectBalanceService).initOpeningBalances(eq("202607"), any(), anyMap());
    }
}