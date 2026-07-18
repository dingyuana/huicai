package com.huicai.module.arap.controller;

import com.huicai.module.arap.service.PaymentPlanService;
import com.huicai.module.arap.service.PaymentPlanService.PaymentPlanGroupVO;
import com.huicai.module.arap.service.PaymentPlanService.PaymentPlanItemVO;
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
class PaymentPlanControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PaymentPlanService service;

    @Test
    @DisplayName("付款计划_期间参数正确传递")
    void generatePaymentPlan_periodParam_applied() throws Exception {
        var items = List.of(new PaymentPlanItemVO(
            "PO2026120001", "INVOICE_IN",
            LocalDate.of(2026, 12, 31), BigDecimal.valueOf(50000),
            0, LocalDate.of(2026, 12, 28), "NORMAL"
        ));
        var groups = List.of(new PaymentPlanGroupVO(
            1L, "供应商A", BigDecimal.valueOf(50000), 1, items
        ));
        when(service.generatePaymentPlan(eq("202612"), isNull())).thenReturn(groups);

        mvc.perform(get("/api/v1/payment-plans")
                        .param("period", "202612"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].vendorName").value("供应商A"));
    }

    @Test
    @DisplayName("付款计划_供应商参数正确传递")
    void generatePaymentPlan_vendorParam_applied() throws Exception {
        when(service.generatePaymentPlan(isNull(), eq(1L))).thenReturn(List.of());

        mvc.perform(get("/api/v1/payment-plans")
                        .param("vendorId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}