/**
 * BudgetController 契约测试 (L3 MockMvc)
 *
 * 验证 API 契约：HTTP 状态码、JSON 响应结构、错误码。
 *
 * @模块: 转换自 RestAssured 版本
 */
package com.huicai.api.rest;

import com.huicai.common.exception.BusinessException;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.config.security.LoginUser;
import com.huicai.sme.budget.service.BudgetService;
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

@WebMvcTest(com.huicai.sme.budget.controller.BudgetController.class)
@AutoConfigureMockMvc(addFilters = false)
class BudgetRestContractTest {

    @Autowired private MockMvc mvc;
    @MockBean private BudgetService service;
    private static final String BASE = "/api/sme/budget/v1/budgets";

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

    @Test @DisplayName("GET /page — 分页")
    void page() throws Exception {
        when(service.pageQuery(any(), any(), anyInt(), anyInt())).thenReturn(null);
        mvc.perform(get(BASE + "/page").param("current", "1").param("size", "10").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("GET /{id} — 详情")
    void getById() throws Exception {
        when(service.getById(anyLong())).thenReturn(null);
        mvc.perform(get(BASE + "/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST — 创建")
    void create() throws Exception {
        when(service.create(any(), any())).thenReturn(null);
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/submit — DRAFT→SUBMITTED")
    void submit() throws Exception {
        when(service.submit(anyLong())).thenReturn(null);
        mvc.perform(post(BASE + "/1/submit").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/approve — SUBMITTED→APPROVED")
    void approve() throws Exception {
        when(service.approve(anyLong())).thenReturn(null);
        mvc.perform(post(BASE + "/1/approve").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/activate — 激活预算")
    void activate() throws Exception {
        when(service.activate(anyLong())).thenReturn(null);
        mvc.perform(post(BASE + "/1/activate").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("GET /check — 预算检查")
    void check() throws Exception {
        when(service.checkBudget(any(), any(), any())).thenReturn(null);
        mvc.perform(get(BASE + "/check").param("subjectId", "1").param("period", "202607").param("amount", "1000").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/submit — 非法状态返回 400")
    void submit_error() throws Exception {
        doThrow(new BusinessException(400, "仅DRAFT状态可提交")).when(service).submit(eq(1L));
        mvc.perform(post(BASE + "/1/submit").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
    }
}