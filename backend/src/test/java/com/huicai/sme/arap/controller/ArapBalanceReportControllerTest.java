package com.huicai.sme.arap.controller;

import com.huicai.common.exception.BusinessException;
import com.huicai.sme.arap.service.ArapBalanceReportService;
import com.huicai.sme.arap.service.ArapBalanceReportService.ArapBalancePartyVO;
import com.huicai.sme.arap.service.ArapBalanceReportService.ArapBalanceSummaryVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class ArapBalanceReportControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ArapBalanceReportService service;

    @Test
    @DisplayName("余额汇总_期间与往来单位参数正确传递")
    void balanceSummary_params_applied() throws Exception {
        var vo = new ArapBalanceSummaryVO(
                "202609", true,
                BigDecimal.valueOf(30000), BigDecimal.ZERO,
                List.of(new ArapBalancePartyVO(1001L, "客户A",
                        BigDecimal.ZERO, BigDecimal.valueOf(50000),
                        BigDecimal.valueOf(20000), BigDecimal.valueOf(30000))),
                List.of());
        when(service.getBalanceSummary(eq("202609"), eq(1001L), eq(2002L))).thenReturn(vo);

        mvc.perform(get("/api/sme/arap/v1/report/balances")
                        .param("period", "202609")
                        .param("customerId", "1001")
                        .param("vendorId", "2002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.period").value("202609"))
                .andExpect(jsonPath("$.data.consistent").value(true));
    }

    @Test
    @DisplayName("余额汇总_无往来单位参数时透传null")
    void balanceSummary_nullFilters_applied() throws Exception {
        var vo = new ArapBalanceSummaryVO("202609", true,
                BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of());
        when(service.getBalanceSummary(eq("202609"), isNull(), isNull())).thenReturn(vo);

        mvc.perform(get("/api/sme/arap/v1/report/balances")
                        .param("period", "202609"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("余额汇总_期间缺失返回业务异常(HTTP200+code400)")
    void balanceSummary_missingPeriod_businessError() throws Exception {
        when(service.getBalanceSummary(isNull(), isNull(), isNull()))
                .thenThrow(new BusinessException(400, "期间必填且必须为6位数字(YYYYMM)"));

        mvc.perform(get("/api/sme/arap/v1/report/balances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("期间必填且必须为6位数字(YYYYMM)"));
    }
}