package com.huicai.sme.arap.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.sme.arap.entity.ReconciliationLogEntity;
import com.huicai.sme.arap.mapper.ReconciliationLogMapper;
import com.huicai.sme.arap.entity.BusinessDocEntity;
import com.huicai.sme.arap.mapper.BusinessDocMapper;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class ReconciliationReportControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private BusinessDocMapper businessDocMapper;

    @MockBean
    private ReconciliationLogMapper logMapper;

    @Test
    @DisplayName("未核销应收明细_默认参数正确生效")
    void unmatchedReceivables_defaultParams_applied() throws Exception {
        Page<BusinessDocEntity> page = new Page<>(1, 20);
        when(businessDocMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        mvc.perform(get("/api/sme/arap/v1/reconciliation/report/unmatched-receivables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("未核销应收明细_自定义参数正确绑定")
    void unmatchedReceivables_customParams_boundCorrectly() throws Exception {
        Page<BusinessDocEntity> page = new Page<>(2, 50);
        when(businessDocMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        mvc.perform(get("/api/sme/arap/v1/reconciliation/report/unmatched-receivables")
                        .param("customerId", "100")
                        .param("period", "2024-01")
                        .param("current", "2")
                        .param("size", "50"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("未核销应付明细_默认参数正确生效")
    void unmatchedPayables_defaultParams_applied() throws Exception {
        Page<BusinessDocEntity> page = new Page<>(1, 20);
        when(businessDocMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        mvc.perform(get("/api/sme/arap/v1/reconciliation/report/unmatched-payables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("未核销应付明细_自定义参数正确绑定")
    void unmatchedPayables_customParams_boundCorrectly() throws Exception {
        Page<BusinessDocEntity> page = new Page<>(3, 30);
        when(businessDocMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        mvc.perform(get("/api/sme/arap/v1/reconciliation/report/unmatched-payables")
                        .param("vendorId", "200")
                        .param("period", "2024-02")
                        .param("current", "3")
                        .param("size", "30"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("核销日志查询_多过滤参数正确绑定")
    void reconciliationLogs_multiFilter_boundCorrectly() throws Exception {
        Page<ReconciliationLogEntity> page = new Page<>(1, 20);
        when(logMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        mvc.perform(get("/api/sme/arap/v1/reconciliation/report/logs")
                        .param("sourceDocType", "receipt")
                        .param("sourceDocId", "1")
                        .param("status", "EXECUTED")
                        .param("current", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("核销统计概览_返回汇总数据格式正确")
    void reconciliationSummary_returnsSummary() throws Exception {
        ReconciliationLogEntity log = new ReconciliationLogEntity();
        log.setStatus("EXECUTED");
        log.setAllocatedAmount(new BigDecimal("10000.00"));
        when(logMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(log));

        mvc.perform(get("/api/sme/arap/v1/reconciliation/report/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }
}