package com.huicai.module.system.controller;

import com.huicai.module.finance.service.impl.ClearDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class ClearDataControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private ClearDataService clearDataService;

    @Test
    @DisplayName("清空银行流水_返回200和删除计数")
    void clearBankStatements_returnsOk() throws Exception {
        when(clearDataService.clearBankStatements()).thenReturn(42);

        mvc.perform(post("/api/v1/system/clear-bank-statements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deleted").value(42))
                .andExpect(jsonPath("$.data.message").value("已清空银行流水及相关数据"));

        verify(clearDataService).clearBankStatements();
    }

    @Test
    @DisplayName("清空发票导入记录_返回200和删除计数")
    void clearInvoiceRecords_returnsOk() throws Exception {
        when(clearDataService.clearInvoiceRecords()).thenReturn(10);

        mvc.perform(post("/api/v1/system/clear-invoice-records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deleted").value(10))
                .andExpect(jsonPath("$.data.message").value("已清空发票导入记录及相关数据"));

        verify(clearDataService).clearInvoiceRecords();
    }

    @Test
    @DisplayName("清空全部数据_返回200和删除计数")
    void clearAll_returnsOk() throws Exception {
        when(clearDataService.clearAll()).thenReturn(99);

        mvc.perform(post("/api/v1/system/clear-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deleted").value(99))
                .andExpect(jsonPath("$.data.message").value("已清空全部数据"));

        verify(clearDataService).clearAll();
    }

    @Test
    @DisplayName("清空所有凭证_返回200和删除计数")
    void clearVouchers_returnsOk() throws Exception {
        when(clearDataService.clearVouchers()).thenReturn(30);

        mvc.perform(post("/api/v1/system/clear-vouchers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deleted").value(30))
                .andExpect(jsonPath("$.data.message").value("已清空所有凭证及相关引用"));

        verify(clearDataService).clearVouchers();
    }

    @Test
    @DisplayName("清空业务单据_返回200和删除计数")
    void clearBusinessDocs_returnsOk() throws Exception {
        when(clearDataService.clearBusinessDocs()).thenReturn(15);

        mvc.perform(post("/api/v1/system/clear-business-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deleted").value(15))
                .andExpect(jsonPath("$.data.message").value("已清空所有业务单据及明细行"));

        verify(clearDataService).clearBusinessDocs();
    }

    @Test
    @DisplayName("清空核销数据_返回200和删除计数")
    void clearSettlements_returnsOk() throws Exception {
        when(clearDataService.clearSettlements()).thenReturn(8);

        mvc.perform(post("/api/v1/system/clear-settlements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deleted").value(8))
                .andExpect(jsonPath("$.data.message").value("已清空核销数据，业务单据核销金额已重置"));

        verify(clearDataService).clearSettlements();
    }
}