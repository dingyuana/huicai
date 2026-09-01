package com.huicai.base.voucher.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.base.balance.service.SubjectBalanceService;
import com.huicai.base.voucher.service.LedgerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class LedgerControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private LedgerService ledgerService;

    @MockBean
    private SubjectBalanceService subjectBalanceService;

    @Test
    @DisplayName("科目余额表_RequestParam正确绑定")
    void subjectBalance_params_boundCorrectly() throws Exception {
        when(ledgerService.subjectBalance(eq("202601"))).thenReturn(List.of());

        mvc.perform(get("/api/base/voucher/v1/ledgers/subject-balance")
                        .param("period", "202601"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("总分类账_RequestParam正确绑定")
    void generalLedger_params_boundCorrectly() throws Exception {
        when(ledgerService.generalLedger(eq(1001L), eq("202601"))).thenReturn(List.of());

        mvc.perform(get("/api/base/voucher/v1/ledgers/general")
                        .param("subjectId", "1001")
                        .param("period", "202601"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("明细账_RequestParam正确绑定")
    void subsidiaryLedger_params_boundCorrectly() throws Exception {
        when(ledgerService.subsidiaryLedger(eq(1001L), eq("202601"), isNull(), isNull())).thenReturn(List.of());

        mvc.perform(get("/api/base/voucher/v1/ledgers/subsidiary")
                        .param("subjectId", "1001")
                        .param("period", "202601"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("明细账_日期范围参数正确绑定")
    void subsidiaryLedger_dateRange_params_boundCorrectly() throws Exception {
        when(ledgerService.subsidiaryLedger(eq(1001L), eq("202601"),
                eq(java.time.LocalDate.of(2026, 1, 1)), eq(java.time.LocalDate.of(2026, 1, 31)))).thenReturn(List.of());

        mvc.perform(get("/api/base/voucher/v1/ledgers/subsidiary")
                        .param("subjectId", "1001")
                        .param("period", "202601")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("试算平衡_RequestParam正确绑定")
    void trialBalance_params_boundCorrectly() throws Exception {
        when(subjectBalanceService.checkTrialBalance(eq("202601"))).thenReturn(Map.of("balanced", true));

        mvc.perform(get("/api/base/voucher/v1/ledgers/trial-balance")
                        .param("period", "202601"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("辅助核算账_参数正确绑定")
    void auxiliaryLedger_params_boundCorrectly() throws Exception {
        when(ledgerService.auxiliaryLedger(eq("customer"), eq("202601"), eq(1001L))).thenReturn(List.of());

        mvc.perform(get("/api/base/voucher/v1/ledgers/auxiliary")
                        .param("dimensionType", "customer")
                        .param("period", "202601")
                        .param("dimensionValue", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("辅助核算账_不带dimensionValue参数绑定为null")
    void auxiliaryLedger_withoutDimensionValue_ok() throws Exception {
        when(ledgerService.auxiliaryLedger(eq("vendor"), eq("202601"), isNull())).thenReturn(List.of());

        mvc.perform(get("/api/base/voucher/v1/ledgers/auxiliary")
                        .param("dimensionType", "vendor")
                        .param("period", "202601"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}