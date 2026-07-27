/**
 * 销项发票 API 契约测试 (L3)
 * 
 * 验证 REST 接口的请求/响应契约：
 * - HTTP 状态码
 * - JSON 响应结构
 * - 参数校验（必填、类型、范围）
 * - 认证授权行为
 * 
 * @模块: SME Tax Module
 * @author Opencode
 */
package com.huicai.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.sme.tax.constant.InvoiceStatus;
import com.huicai.base.business.entity.OutputInvoiceEntity;
import com.huicai.base.business.mapper.OutputInvoiceMapper;
import com.huicai.base.business.mapper.BusinessDocMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.mockito.Mockito;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

@WebMvcTest(TaxController.class)
class TaxApiContractTest {

    @Autowired
    private MockMvc mvcTest;

    @MockBean
    private OutputInvoiceMapper invoiceMapper;

    @MockBean
    private BusinessDocMapper businessDocMapper;

    @BeforeEach
    void setUp() {
        // 每次测试前的初始化
    }

    @Test
    @WithUserDetails(username = "admin")
    void getOutputInvoiceList_success() throws Exception {
        when(invoiceMapper.selectAny()).thenReturn(java.util.Collections.emptyList());

        mvcTest.perform(get("/api/sme/tax/v1/tax/output-invoices")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isArray()))
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    @WithUserDetails(username = "accountant")
    void postOutputInvoice_createValidData_success() throws Exception {
        String json = "{\"customerName\":\"测试客户\",\"invoiceNo\":\"TEST-001\",\"amount\":1000.00,\"taxRate\":13}";
        
        mvcTest.perform(post("/api/sme/tax/v1/tax/output-invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.customerName", is("测试客户")))
                .andExpect(jsonPath("$.invoiceNo", is("TEST-001")))
                .andExpect(jsonPath("$.amount", is(1000.00)));
    }

    @Test
    @WithUserDetails(username = "accountant")
    void confirmOutput_invoiceValidState_success() throws Exception {
        OutputInvoiceEntity inv = new OutputInvoiceEntity();
        inv.setId(1L);
        inv.setStatus(InvoiceStatus.PENDING_REVIEW);
        when(invoiceMapper.selectById(1L)).thenReturn(inv);
        when(businessDocMapper.selectCount(Mockito.any())).thenReturn(0L);

        mvcTest.perform(post("/api/sme/tax/v1/tax/output-invoices/1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONFIRMED")));
    }

    @Test
    @WithUserDetails(username = "accountant")
    void confirmOutput_invoiceWrongState_fail() throws Exception {
        OutputInvoiceEntity inv = new OutputInvoiceEntity();
        inv.setId(1L);
        inv.setStatus(InvoiceStatus.CONFIRMED);
        when(invoiceMapper.selectById(1L)).thenReturn(inv);

        mvcTest.perform(post("/api/sme/tax/v1/tax/output-invoices/1/confirm"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("msg", containsString("仅待审核状态可确认")));
    }

    @Test
    @WithUserDetails(username = "operator")
    void confirmOutput_invoiceUnauthorized_fail() throws Exception {
        mvcTest.perform(post("/api/sme/tax/v1/tax/output-invoices/1/confirm"))
                .andExpect(status().isForbidden());
    }
}
