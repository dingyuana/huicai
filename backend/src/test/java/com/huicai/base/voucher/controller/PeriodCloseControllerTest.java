package com.huicai.base.voucher.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.base.voucher.service.PeriodCloseService;
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
class PeriodCloseControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private PeriodCloseService periodCloseService;

    @Test
    @DisplayName("结账检查_RequestParam正确绑定")
    void check_requestParam_boundCorrectly() throws Exception {
        when(periodCloseService.checkBeforeClose(eq("202601"))).thenReturn(Map.of("canClose", true));

        mvc.perform(get("/api/base/voucher/v1/period-close/check")
                        .param("period", "202601"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("损益结转_RequestParam正确绑定")
    void profitCarryover_requestParam_boundCorrectly() throws Exception {
        when(periodCloseService.generateProfitCarryOver(eq("202601"), anyLong())).thenReturn(1L);

        mvc.perform(post("/api/base/voucher/v1/period-close/profit-carryover")
                        .param("period", "202601"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("结账_RequestParam正确绑定")
    void close_requestParam_boundCorrectly() throws Exception {
        doNothing().when(periodCloseService).closePeriod(eq("202601"), anyLong());

        mvc.perform(post("/api/base/voucher/v1/period-close/close")
                        .param("period", "202601"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("反结账_RequestParam正确绑定")
    void reopen_requestParam_boundCorrectly() throws Exception {
        doNothing().when(periodCloseService).reopenPeriod(eq("202601"), anyLong());

        mvc.perform(post("/api/base/voucher/v1/period-close/reopen")
                        .param("period", "202601"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("查询结账日志_RequestParam正确绑定")
    void log_requestParam_boundCorrectly() throws Exception {
        when(periodCloseService.listCloseLog(eq("202601"))).thenReturn(List.of());

        mvc.perform(get("/api/base/voucher/v1/period-close/log")
                        .param("period", "202601"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}