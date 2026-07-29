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

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.base.business.entity.OutputInvoiceEntity;
import com.huicai.base.business.service.OutputInvoiceStateMachineService;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.tax.service.InputInvoiceStateMachineService;
import com.huicai.sme.tax.service.TaxService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaxApiContractTest {

    @Autowired
    private MockMvc mvcTest;

    @MockBean
    private TaxService taxService;

    @MockBean
    private OutputInvoiceStateMachineService stateMachineService;

    @MockBean
    private InputInvoiceStateMachineService inputStateMachineService;

    @Test
    @WithMockUser
    void getOutputInvoiceList_success() throws Exception {
        when(taxService.pageQueryOutput(any(), any(), any(), any(), any(), any()))
                .thenReturn(new Page<>());
        mvcTest.perform(get("/api/sme/tax/v1/tax/output-invoices/page"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    @WithMockUser
    void postOutputInvoice_createValidData_success() throws Exception {
        OutputInvoiceEntity created = new OutputInvoiceEntity();
        created.setId(1L);
        created.setCustomerName("测试客户");
        when(taxService.createOutput(any())).thenReturn(created);
        String json = "{\"customerName\":\"测试客户\",\"invoiceNo\":\"TEST-001\",\"amount\":1000.00,\"taxRate\":13}";
        mvcTest.perform(post("/api/sme/tax/v1/tax/output-invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @WithMockUser
    void confirmOutput_invoiceValidState_success() throws Exception {
        doNothing().when(stateMachineService).confirm(eq(1L), anyLong());
        mvcTest.perform(post("/api/sme/tax/v1/tax/output-invoices/1/confirm"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void confirmOutput_invoiceWrongState_fail() throws Exception {
        // 注意：BusinessException 被全局异常处理器捕获，返回 HTTP 200 + code=400
        // confirm() 是 void 方法，必须用 doThrow() 而非 when().thenThrow()
        doThrow(new BusinessException(400, "仅待审核状态可确认"))
                .when(stateMachineService).confirm(eq(1L), anyLong());
        mvcTest.perform(post("/api/sme/tax/v1/tax/output-invoices/1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(400)));
    }

    @Test
    void confirmOutput_invoiceUnauthorized_fail() throws Exception {
        mvcTest.perform(post("/api/sme/tax/v1/tax/output-invoices/1/confirm"))
                .andExpect(status().isUnauthorized());
    }
}