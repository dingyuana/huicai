/**
 * 销项发票 API 契约测试 (L3) 
 * 
 * @模块: SME Tax Module
 */
package com.huicai.api.controller;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.huicai.sme.tax.constant.InvoiceStatus;
import com.huicai.base.business.entity.OutputInvoiceEntity;
import com.huicai.base.business.mapper.OutputInvoiceMapper;
import com.huicai.base.business.mapper.BusinessDocMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(com.huicai.sme.tax.controller.TaxController.class)
class TaxApiContractTest {

    @Autowired
    private MockMvc mvcTest;

    @MockBean
    private OutputInvoiceMapper invoiceMapper;

    @MockBean
    private BusinessDocMapper businessDocMapper;

    @Test
    void getOutputInvoiceList_success() throws Exception {
        when(invoiceMapper.selectList(null)).thenReturn(java.util.Collections.emptyList());
        mvcTest.perform(get("/api/sme/tax/v1/tax/output-invoices"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void postOutputInvoice_createValidData_success() throws Exception {
        String json = "{\"customerName\":\"测试客户\",\"invoiceNo\":\"TEST-001\",\"amount\":1000.00,\"taxRate\":13}";
        mvcTest.perform(post("/api/sme/tax/v1/tax/output-invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));
    }

    @Test
    void confirmOutput_invoiceValidState_success() throws Exception {
        OutputInvoiceEntity inv = new OutputInvoiceEntity();
        inv.setId(1L);
        inv.setStatus(InvoiceStatus.PENDING_REVIEW);
        when(invoiceMapper.selectById(1L)).thenReturn(inv);
        when(businessDocMapper.selectCount(any())).thenReturn(0L);
        mvcTest.perform(post("/api/sme/tax/v1/tax/output-invoices/1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONFIRMED")));
    }

    @Test
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
    void confirmOutput_invoiceUnauthorized_fail() throws Exception {
        mvcTest.perform(post("/api/sme/tax/v1/tax/output-invoices/1/confirm"))
                .andExpect(status().isForbidden());
    }
}
