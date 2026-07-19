package com.huicai.sme.arap.controller;

import com.huicai.sme.arap.service.AgingAnalysisService;
import com.huicai.sme.arap.service.AgingAnalysisService.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class AgingAnalysisControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AgingAnalysisService service;

    @Test
    @DisplayName("账龄汇总_期间参数正确传递")
    void agingSummary_periodParam_applied() throws Exception {
        var vo = new AgingSummaryVO(
                LocalDate.of(2026, 12, 31), "202612",
                new AgingSummary(BigDecimal.valueOf(100000), BigDecimal.valueOf(30000), "30%"),
                List.of(new AgingBucket("信用期内", BigDecimal.valueOf(70000), 5, "70%"))
        );
        when(service.getAgingSummary(eq("202612"), isNull())).thenReturn(vo);

        mvc.perform(get("/api/sme/arap/v1/aging-analysis/summary")
                        .param("period", "202612"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("到期债权表_日期参数正确传递")
    void dueReceivables_dateParam_applied() throws Exception {
        var vo = new DueReceivablesVO(LocalDate.of(2026, 12, 31),
                BigDecimal.valueOf(50000), 2, List.of());
        when(service.getDueReceivables(any(), isNull())).thenReturn(vo);

        mvc.perform(get("/api/sme/arap/v1/aging-analysis/due-receivables")
                        .param("date", "2026-12-31"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("查询预警列表_等级参数正确传递")
    void alerts_levelParam_applied() throws Exception {
        when(service.getAlerts(eq("MODERATE"), eq("ACTIVE"), isNull()))
                .thenReturn(List.of());

        mvc.perform(get("/api/sme/arap/v1/aging-analysis/alerts")
                        .param("alertLevel", "MODERATE")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("生成预警_返回生成数量")
    void generateAlerts_returnsCount() throws Exception {
        when(service.generateAlerts("202612")).thenReturn(5);

        mvc.perform(post("/api/sme/arap/v1/aging-analysis/alerts/generate")
                        .param("period", "202612"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(5));
    }

    @Test
    @DisplayName("忽略预警_返回成功")
    void dismissAlert_returnsOk() throws Exception {
        mvc.perform(post("/api/sme/arap/v1/aging-analysis/alerts/1/dismiss"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}