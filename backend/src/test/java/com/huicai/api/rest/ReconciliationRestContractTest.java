/**
 * ReconciliationController 契约测试 (L3 MockMvc)
 *
 * 验证 API 契约：HTTP 状态码、JSON 响应结构、错误码。
 *
 * @模块: 转换自 RestAssured 版本
 */
package com.huicai.api.rest;

import com.huicai.common.exception.BusinessException;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.config.security.LoginUser;
import com.huicai.sme.arap.service.ReconciliationService;
import com.huicai.sme.arap.service.ReconciliationToleranceService;
import com.huicai.sme.arap.service.impl.ReconciliationServiceImpl;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(com.huicai.sme.arap.controller.ReconciliationController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReconciliationRestContractTest {

    @Autowired private MockMvc mvc;
    @MockBean private ReconciliationServiceImpl reconciliationServiceImpl;
    @MockBean private ReconciliationToleranceService toleranceService;
    private static final String BASE = "/api/sme/arap/v1/reconciliation";

    @MockBean private com.huicai.config.security.JwtProvider jwtProvider;
    @MockBean private com.huicai.base.system.service.impl.UserDetailsServiceImpl userDetailsService;
    @MockBean private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        // Mock the ReconciliationServiceImpl which satisfies both ReconciliationService and ReconciliationServiceImpl fields
        when(reconciliationServiceImpl.recommendReceipt(anyLong(), anyString(), anyLong(), any(), any(), anyString())).thenReturn(null);
        when(reconciliationServiceImpl.recommendPayment(anyLong(), anyString(), anyLong(), any(), any(), anyString())).thenReturn(null);
        when(reconciliationServiceImpl.execute(any())).thenReturn(null);
        when(reconciliationServiceImpl.getRecords(anyString(), anyLong())).thenReturn(null);
        doNothing().when(reconciliationServiceImpl).reverse(anyLong(), anyString());
        when(reconciliationServiceImpl.approve(anyLong())).thenReturn(null);
        when(reconciliationServiceImpl.autoReconcileFifo(anyLong(), anyString(), any(), anyString(), anyLong(), any(), anyString())).thenReturn(null);
        UserEntity user = new UserEntity();
        user.setId(1L); user.setUsername("admin"); user.setPassword("encoded");
        user.setUserType("AGENCY"); user.setAgencyId(1L); user.setAgencyRole("AGENCY_ADMIN");
        user.setStatus("ACTIVE"); user.setDeleted(0);
        LoginUser loginUser = new LoginUser(user, List.of(), null, 1L, "AGENCY", "AGENCY_ADMIN");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test @DisplayName("POST /receipt/{id}/recommend — 应收核销推荐")
    void receiptRecommend() throws Exception {
        mvc.perform(post(BASE + "/receipt/1/recommend")
                .param("sourceDocType", "INVOICE_OUT").param("customerId", "1").param("amount", "1000")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /payment/{id}/recommend — 应付核销推荐")
    void paymentRecommend() throws Exception {
        mvc.perform(post(BASE + "/payment/1/recommend")
                .param("sourceDocType", "INVOICE_OUT").param("vendorId", "1").param("amount", "1000")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /execute — 执行核销")
    void execute() throws Exception {
        mvc.perform(post(BASE + "/execute").contentType(MediaType.APPLICATION_JSON).content("{\"sourceDocId\":1,\"targetDocId\":2,\"amount\":5000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /execute — 参数错误返回 400")
    void execute_error() throws Exception {
        doThrow(new BusinessException(400, "核销金额不能大于未核销金额")).when(reconciliationServiceImpl).execute(any());
        mvc.perform(post(BASE + "/execute").contentType(MediaType.APPLICATION_JSON).content("{\"sourceDocId\":1,\"targetDocId\":2,\"amount\":999999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
    }

    @Test @DisplayName("GET /records — 核销记录查询")
    void records() throws Exception {
        mvc.perform(get(BASE + "/records").param("sourceDocType", "INVOICE_OUT").param("sourceDocId", "1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/reverse — 反核销")
    void reverse() throws Exception {
        mvc.perform(post(BASE + "/1/reverse").param("reason", "test").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/approve — 核销审批")
    void approve() throws Exception {
        mvc.perform(post(BASE + "/1/approve").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /auto-fifo — 自动 FIFO 核销")
    void autoFifo() throws Exception {
        String json = "{\"partyId\":1,\"targetDocType\":\"INVOICE_OUT\",\"amount\":5000,\"sourceDocType\":\"INVOICE_OUT\",\"sourceDocId\":1,\"period\":\"202607\",\"summary\":\"测试自动核销\"}";
        mvc.perform(post(BASE + "/auto-fifo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }
}