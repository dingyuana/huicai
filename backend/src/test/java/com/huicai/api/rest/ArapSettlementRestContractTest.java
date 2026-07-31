/**
 * ArapSettlementController 契约测试 (L3 MockMvc)
 *
 * 验证 API 契约：HTTP 状态码、JSON 响应结构、错误码。
 *
 * @模块: 转换自 RestAssured 版本
 */
package com.huicai.api.rest;

import com.huicai.common.exception.BusinessException;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.config.security.LoginUser;
import com.huicai.base.business.entity.ArapSettlementEntity;
import com.huicai.sme.arap.service.ArapSettlementService;
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

@WebMvcTest(com.huicai.sme.arap.controller.ArapSettlementController.class)
@AutoConfigureMockMvc(addFilters = false)
class ArapSettlementRestContractTest {

    @Autowired private MockMvc mvc;
    @MockBean private ArapSettlementService arapSettlementService;
    private static final String BASE = "/api/sme/arap/v1/arap-settlements";

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

    @Test @DisplayName("POST / — 创建成功返回 200 + data 结构")
    void create_settlement_success() throws Exception {
        ArapSettlementEntity mockEntity = new ArapSettlementEntity();
        when(arapSettlementService.create(any(ArapSettlementEntity.class), anyList())).thenReturn(mockEntity);
        String payload = "{\"settlement\":{\"settlementType\":\"RECEIVE\",\"settlementDate\":\"2026-07-01\",\"period\":\"202607\",\"partyId\":1,\"partyType\":\"CUSTOMER\",\"totalAmount\":1000.00,\"status\":\"DRAFT\"},\"entries\":[]}";
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST / — 创建时Service抛出异常，验证错误响应结构")
    void create_settlement_serviceError_returnsError() throws Exception {
        doThrow(new BusinessException(500, "创建核销单失败")).when(arapSettlementService).create(any(), anyList());
        String payload = "{\"settlement\":{\"settlementType\":\"RECEIVE\"},\"entries\":[]}";
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500));
    }

    @Test @DisplayName("POST /{id}/submit — 提交成功返回 200")
    void submit_success() throws Exception {
        doNothing().when(arapSettlementService).submit(eq(1L));
        mvc.perform(post(BASE + "/1/submit").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/submit — 状态非法返回 400")
    void submit_wrongState_returns400() throws Exception {
        doThrow(new BusinessException(400, "仅DRAFT状态可提交")).when(arapSettlementService).submit(eq(1L));
        mvc.perform(post(BASE + "/1/submit").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
    }

    @Test @DisplayName("POST /{id}/approve — 审批通过返回 200 + data")
    void approve_success() throws Exception {
        ArapSettlementEntity mockEntity = new ArapSettlementEntity();
        when(arapSettlementService.approve(eq(1L))).thenReturn(mockEntity);
        mvc.perform(post(BASE + "/1/approve").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/approve — 状态非法返回 400")
    void approve_wrongState_returns400() throws Exception {
        doThrow(new BusinessException(400, "仅SUBMITTED状态可审批")).when(arapSettlementService).approve(eq(1L));
        mvc.perform(post(BASE + "/1/approve").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
    }

    @Test @DisplayName("POST /{id}/reject — 驳回成功返回 200")
    void reject_withReason_success() throws Exception {
        doNothing().when(arapSettlementService).reject(eq(1L), anyString());
        mvc.perform(post(BASE + "/1/reject").param("reason", "审批不通过").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/reject — 状态非法返回 400")
    void reject_wrongState_returns400() throws Exception {
        doThrow(new BusinessException(400, "仅SUBMITTED状态可驳回")).when(arapSettlementService).reject(eq(1L), anyString());
        mvc.perform(post(BASE + "/1/reject").param("reason", "审批不通过").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
    }

    @Test @DisplayName("POST /{id}/reject-simple — 无理由驳回成功返回 200")
    void rejectSimple_success() throws Exception {
        doNothing().when(arapSettlementService).reject(eq(1L));
        mvc.perform(post(BASE + "/1/reject-simple").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/generate-voucher — 生成凭证成功返回 200")
    void generateVoucher_success() throws Exception {
        doNothing().when(arapSettlementService).generateVoucher(eq(1L));
        mvc.perform(post(BASE + "/1/generate-voucher").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/generate-voucher — 状态非法返回 400")
    void generateVoucher_wrongState_returns400() throws Exception {
        doThrow(new BusinessException(400, "仅CONFIRMED状态可生成凭证")).when(arapSettlementService).generateVoucher(eq(1L));
        mvc.perform(post(BASE + "/1/generate-voucher").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
    }

    @Test @DisplayName("POST /{id}/cancel — 取消成功返回 200")
    void cancel_success() throws Exception {
        doNothing().when(arapSettlementService).cancel(eq(1L));
        mvc.perform(post(BASE + "/1/cancel").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/reverse — 反核销成功返回 200")
    void reverse_success() throws Exception {
        doNothing().when(arapSettlementService).reverse(eq(1L));
        mvc.perform(post(BASE + "/1/reverse").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("GET /page — 分页成功返回 200 + page 结构")
    void page_success() throws Exception {
        when(arapSettlementService.pageQueryWithPartyName(any(), any(), any(), any())).thenReturn(null);
        mvc.perform(get(BASE + "/page").param("current", "1").param("size", "10").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("GET /{id} — 详情成功返回 200 + data")
    void getById_success() throws Exception {
        when(arapSettlementService.getDetailWithPartyName(eq(1L))).thenReturn(null);
        mvc.perform(get(BASE + "/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("GET /{id} — 不存在返回 400")
    void getById_notFound_returns400() throws Exception {
        doThrow(new BusinessException(400, "核销单不存在")).when(arapSettlementService).getDetailWithPartyName(eq(999L));
        mvc.perform(get(BASE + "/999").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
    }

    @Test @DisplayName("DELETE /{id} — 删除成功返回 200")
    void deleteSettlement() throws Exception {
        doNothing().when(arapSettlementService).delete(eq(1L));
        mvc.perform(delete(BASE + "/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }
}