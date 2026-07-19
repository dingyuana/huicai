package com.huicai.sme.arap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.sme.arap.entity.CustomerStatementEntity;
import com.huicai.sme.arap.service.CustomerStatementService;
import com.huicai.sme.arap.service.CustomerStatementService.DisputeRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class CustomerStatementControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private CustomerStatementService service;

    @Test
    @DisplayName("生成对账单_参数正确绑定")
    void generateStatements_paramsBound() throws Exception {
        when(service.generateStatements(anyList(), eq("202612")))
                .thenReturn(List.of(new CustomerStatementEntity()));

        mvc.perform(post("/api/sme/arap/v1/customer-statements/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(
                                java.util.Map.of("customerIds", List.of(1L, 2L), "period", "202612"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("发送对账单_状态流转")
    void sendStatement_returnsOk() throws Exception {
        mvc.perform(post("/api/sme/arap/v1/customer-statements/1/send"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("确认对账单_状态流转")
    void confirmStatement_returnsOk() throws Exception {
        mvc.perform(post("/api/sme/arap/v1/customer-statements/1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("提交争议_参数正确绑定")
    void disputeStatement_paramsBound() throws Exception {
        var req = new DisputeRequest("INV001", "AMOUNT_MISMATCH",
                java.math.BigDecimal.valueOf(1000), java.math.BigDecimal.valueOf(950), "金额不符");
        mvc.perform(post("/api/sme/arap/v1/customer-statements/1/dispute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("分页查询_默认参数")
    void pageStatements_defaultParams() throws Exception {
        mvc.perform(get("/api/sme/arap/v1/customer-statements/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("解决未达账项_返回成功")
    void resolveOutstandingItem_returnsOk() throws Exception {
        mvc.perform(post("/api/sme/arap/v1/outstanding-items/1/resolve"))
                .andExpect(status().isOk());
    }
}