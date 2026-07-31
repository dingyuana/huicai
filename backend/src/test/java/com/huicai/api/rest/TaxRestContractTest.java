/**
 * TaxController 契约测试 (L3 MockMvc)
 *
 * 验证 API 契约：HTTP 状态码、JSON 响应结构、错误码。
 *
 * @模块: 转换自 RestAssured 版本
 */
package com.huicai.api.rest;

import com.huicai.common.exception.BusinessException;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.config.security.LoginUser;
import com.huicai.sme.tax.service.TaxService;
import com.huicai.base.business.service.OutputInvoiceStateMachineService;
import com.huicai.sme.tax.service.InputInvoiceStateMachineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(com.huicai.sme.tax.controller.TaxController.class)
@AutoConfigureMockMvc(addFilters = false)
class TaxRestContractTest {

    @Autowired private MockMvc mvc;

    @MockBean private TaxService taxService;
    @MockBean private OutputInvoiceStateMachineService stateMachineService;
    @MockBean private InputInvoiceStateMachineService inputStateMachineService;
    @MockBean private com.huicai.config.security.JwtProvider jwtProvider;
    @MockBean private com.huicai.base.system.service.impl.UserDetailsServiceImpl userDetailsService;
    @MockBean private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        UserEntity user = new UserEntity();
        user.setId(1L); user.setUsername("admin"); user.setPassword("encoded");
        user.setUserType("AGENCY"); user.setAgencyId(1L); user.setAgencyRole("AGENCY_ADMIN");
        user.setStatus("ACTIVE"); user.setDeleted(0);
        LoginUser loginUser = new LoginUser(user, List.of(), null, 1L, "AGENCY", "AGENCY_ADMIN");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test @DisplayName("GET /types/list — 返回 200 + JSON 数组")
    void getTaxTypeList_success() throws Exception {
        when(taxService.listAllTaxTypes()).thenReturn(Collections.emptyList());
        mvc.perform(get("/api/sme/tax/v1/tax/types/list").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test @DisplayName("POST /output-invoices — 缺少必填字段返回 400")
    void createOutputInvoice_missingFields_returns400() throws Exception {
        doThrow(new BusinessException(400, "缺少必填字段")).when(taxService).createOutput(any());
        String payload = "{\"invoiceNo\":\"REST-ASSURED-TEST-INV\",\"amount\":1000.00,\"taxRate\":13}";
        mvc.perform(post("/api/sme/tax/v1/tax/output-invoices")
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test @DisplayName("POST /output-invoices/{id}/confirm — 状态非法返回 400")
    void confirmOutputInvoice_wrongState_returns400() throws Exception {
        doThrow(new BusinessException(400, "仅待审核状态可确认")).when(stateMachineService).confirm(eq(1L), anyLong());
        mvc.perform(post("/api/sme/tax/v1/tax/output-invoices/1/confirm").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test @DisplayName("POST /output-invoices/{id}/confirm — 成功返回 200")
    void confirmOutputInvoice_success() throws Exception {
        doNothing().when(stateMachineService).confirm(eq(1L), anyLong());
        mvc.perform(post("/api/sme/tax/v1/tax/output-invoices/1/confirm").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}