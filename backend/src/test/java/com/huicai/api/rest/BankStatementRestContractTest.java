/**
 * BankStatementController 契约测试 (L3 MockMvc)
 *
 * 验证 API 契约：HTTP 状态码、JSON 响应结构、错误码。
 *
 * @模块: 转换自 RestAssured 版本
 */
package com.huicai.api.rest;

import com.huicai.common.exception.BusinessException;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.config.security.LoginUser;
import com.huicai.sme.cash.service.BankStatementService;
import com.huicai.sme.cash.service.impl.BankStatementExcelImportService;
import com.huicai.sme.arap.service.impl.AutoGenerationService;
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

@WebMvcTest(com.huicai.sme.cash.controller.BankStatementController.class)
@AutoConfigureMockMvc(addFilters = false)
class BankStatementRestContractTest {

    @Autowired private MockMvc mvc;
    @MockBean private BankStatementService service;
    @MockBean private BankStatementExcelImportService excelImportService;
    @MockBean private AutoGenerationService autoGenerationService;
    private static final String BASE = "/api/sme/cash/v1/bank-statements";

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

    @Test @DisplayName("GET /page — 分页查询")
    void page() throws Exception {
        when(service.pageQuery(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(null);
        mvc.perform(get(BASE + "/page").param("current", "1").param("size", "10").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("GET /{id} — 详情")
    void getById() throws Exception {
        when(service.getDetail(anyLong())).thenReturn(null);
        mvc.perform(get(BASE + "/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/review — 审核流水")
    void review() throws Exception {
        when(service.review(anyLong(), any())).thenReturn(null);
        mvc.perform(post(BASE + "/1/review").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /batch-review — 批量审核")
    void batchReview() throws Exception {
        when(service.batchReview(any(), any())).thenReturn(new BankStatementService.BatchResult(2, 1, List.of()));
        mvc.perform(post(BASE + "/batch-review").contentType(MediaType.APPLICATION_JSON).content("[1,2]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/audit — 审计流水")
    void audit() throws Exception {
        when(service.audit(anyLong(), any())).thenReturn(null);
        mvc.perform(post(BASE + "/1/audit").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /{id}/review — 不存在返回 400")
    void review_error() throws Exception {
        doThrow(new BusinessException(400, "银行流水不存在")).when(service).review(eq(999L), any());
        mvc.perform(post(BASE + "/999/review").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
    }
}