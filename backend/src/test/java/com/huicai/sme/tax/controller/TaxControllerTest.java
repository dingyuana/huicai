package com.huicai.sme.tax.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.base.business.entity.InputInvoiceEntity;
import com.huicai.base.business.entity.OutputInvoiceEntity;
import com.huicai.base.business.service.OutputInvoiceStateMachineService;
import com.huicai.sme.tax.entity.TaxDeclarationEntity;
import com.huicai.sme.tax.entity.TaxTypeEntity;
import com.huicai.sme.tax.service.InputInvoiceStateMachineService;
import com.huicai.sme.tax.service.TaxService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class TaxControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private TaxService taxService;

    @MockBean
    private OutputInvoiceStateMachineService outputStateMachine;

    @MockBean
    private InputInvoiceStateMachineService inputStateMachine;

    // ===== 税种管理 =====

    @Test
    @DisplayName("税率分页查询_参数正确绑定")
    void typesPage_defaultParams_applied() throws Exception {
        when(taxService.pageQueryTaxType(any(), eq(1), eq(20))).thenReturn(new Page<>());

        mvc.perform(get("/api/sme/tax/v1/tax/types/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("新增税率_RequestBody正确解析")
    void createTaxType_requestBody_parsedCorrectly() throws Exception {
        TaxTypeEntity entity = new TaxTypeEntity();
        entity.setName("增值税");
        entity.setRate(new BigDecimal("13"));

        TaxTypeEntity created = new TaxTypeEntity();
        created.setId(1L);
        when(taxService.createTaxType(any())).thenReturn(created);

        mvc.perform(post("/api/sme/tax/v1/tax/types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(entity)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    // ===== 进项发票 =====

    @Test
    @DisplayName("进项发票分页查询_参数正确绑定")
    void inputInvoicesPage_params_applied() throws Exception {
        when(taxService.pageQueryInput(any(), any(), any(), eq(1), eq(20))).thenReturn(new Page<>());

        mvc.perform(get("/api/sme/tax/v1/tax/input-invoices/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("进项发票确认_状态机端点")
    void inputInvoiceConfirm_confirmEndpoint() throws Exception {
        doNothing().when(inputStateMachine).confirm(eq(1L), anyLong());

        mvc.perform(post("/api/sme/tax/v1/tax/input-invoices/1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(inputStateMachine).confirm(eq(1L), anyLong());
    }

    @Test
    @DisplayName("进项发票提交审核_状态机端点")
    void inputInvoiceSubmitReview_submitEndpoint() throws Exception {
        doNothing().when(inputStateMachine).submitForReview(eq(1L), anyLong());

        mvc.perform(post("/api/sme/tax/v1/tax/input-invoices/1/submit-review"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("进项发票驳回_状态机端点")
    void inputInvoiceReject_rejectEndpoint() throws Exception {
        doNothing().when(inputStateMachine).reject(eq(1L), anyLong(), anyString());

        mvc.perform(post("/api/sme/tax/v1/tax/input-invoices/1/reject")
                        .param("reason", "发票信息有误"))
                .andExpect(status().isOk());
    }

    // ===== 销项发票 =====

    @Test
    @DisplayName("销项发票分页查询_参数正确绑定")
    void outputInvoicesPage_params_applied() throws Exception {
        when(taxService.pageQueryOutput(any(), any(), any(), any(), eq(1), eq(20))).thenReturn(new Page<>());

        mvc.perform(get("/api/sme/tax/v1/tax/output-invoices/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("销项发票确认_状态机端点（发票500错误根因路径）")
    void outputInvoiceConfirm_confirmEndpoint() throws Exception {
        doNothing().when(outputStateMachine).confirm(eq(1L), anyLong());

        mvc.perform(post("/api/sme/tax/v1/tax/output-invoices/1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(outputStateMachine).confirm(eq(1L), anyLong());
    }

    @Test
    @DisplayName("销项发票新增_RequestBody正确解析")
    void createOutput_requestBody_parsedCorrectly() throws Exception {
        OutputInvoiceEntity entity = new OutputInvoiceEntity();
        entity.setCustomerName("测试客户");
        entity.setAmount(BigDecimal.valueOf(10000));

        when(taxService.createOutput(any())).thenReturn(entity);

        mvc.perform(post("/api/sme/tax/v1/tax/output-invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(entity)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("销项发票驳回_状态机端点")
    void outputInvoiceReject_rejectEndpoint() throws Exception {
        doNothing().when(outputStateMachine).reject(eq(1L), anyLong(), anyString());

        mvc.perform(post("/api/sme/tax/v1/tax/output-invoices/1/reject")
                        .param("reason", "客户信息不完整"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("增值税计算_参数正确绑定")
    void calculateVat_params_applied() throws Exception {
        when(taxService.calculateVat(eq("202601"))).thenReturn(Map.of("totalVat", BigDecimal.valueOf(13000)));

        mvc.perform(get("/api/sme/tax/v1/tax/vat/calculate")
                        .param("period", "202601"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ===== 申报管理 =====

    @Test
    @DisplayName("申报分页查询_参数正确绑定")
    void declarationsPage_params_applied() throws Exception {
        when(taxService.pageQueryDeclaration(any(), eq(1), eq(20))).thenReturn(new Page<>());

        mvc.perform(get("/api/sme/tax/v1/tax/declarations/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("新增申报_RequestBody正确解析")
    void createDeclaration_requestBody_parsedCorrectly() throws Exception {
        TaxDeclarationEntity entity = new TaxDeclarationEntity();
        entity.setPeriod("202601");

        TaxDeclarationEntity created = new TaxDeclarationEntity();
        created.setId(1L);
        when(taxService.createDeclaration(any())).thenReturn(created);

        mvc.perform(post("/api/sme/tax/v1/tax/declarations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(entity)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("提交申报_状态流转端点")
    void submitDeclaration_statusTransition() throws Exception {
        TaxDeclarationEntity entity = new TaxDeclarationEntity();
        entity.setId(1L);
        entity.setStatus("SUBMITTED");
        when(taxService.submitDeclaration(eq(1L))).thenReturn(entity);

        mvc.perform(post("/api/sme/tax/v1/tax/declarations/1/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }
}