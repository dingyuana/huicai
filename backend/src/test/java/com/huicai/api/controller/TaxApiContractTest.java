/**
 * 销项发票 API 契约测试 (L3 MockMvc)
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
import com.huicai.base.system.entity.UserEntity;
import com.huicai.common.exception.BusinessException;
import com.huicai.config.security.LoginUser;
import com.huicai.sme.tax.service.InputInvoiceStateMachineService;
import com.huicai.sme.tax.service.TaxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

@WebMvcTest(com.huicai.sme.tax.controller.TaxController.class)
@AutoConfigureMockMvc(addFilters = false)
class TaxApiContractTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private TaxService taxService;

    @MockBean
    private OutputInvoiceStateMachineService stateMachineService;

    @MockBean
    private InputInvoiceStateMachineService inputStateMachineService;
    @MockBean
    private com.huicai.config.security.JwtProvider jwtProvider;
    @MockBean
    private com.huicai.base.system.service.impl.UserDetailsServiceImpl userDetailsService;
    @MockBean
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

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

    @Test
    void getOutputInvoiceList_success() throws Exception {
        when(taxService.pageQueryOutput(any(), any(), any(), any(), any(), any()))
                .thenReturn(new Page<>());
        mvc.perform(get("/api/sme/tax/v1/tax/output-invoices/page"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void postOutputInvoice_createValidData_success() throws Exception {
        OutputInvoiceEntity created = new OutputInvoiceEntity();
        created.setId(1L);
        created.setCustomerName("测试客户");
        when(taxService.createOutput(any())).thenReturn(created);
        String json = "{\"customerName\":\"测试客户\",\"invoiceNo\":\"TEST-001\",\"amount\":1000.00,\"taxRate\":13}";
        mvc.perform(post("/api/sme/tax/v1/tax/output-invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    void confirmOutput_invoiceValidState_success() throws Exception {
        doNothing().when(stateMachineService).confirm(eq(1L), anyLong());
        mvc.perform(post("/api/sme/tax/v1/tax/output-invoices/1/confirm"))
                .andExpect(status().isOk());
    }

    @Test
    void confirmOutput_invoiceWrongState_fail() throws Exception {
        doThrow(new BusinessException(400, "仅待审核状态可确认"))
                .when(stateMachineService).confirm(eq(1L), anyLong());
        mvc.perform(post("/api/sme/tax/v1/tax/output-invoices/1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(400)));
    }
}