/**
 * SalaryBillController 契约测试 (L1 MockMvc / @WebMvcTest)
 *
 * SPEC-P143：工资单端点 —— HTTP 契约、入参校验面与 userId 透传。
 * 权限面由 PermissionManifestGuardRealDBTest 守卫（manifest 登记行 + V197 授权）。
 */
package com.huicai.api.rest;

import com.huicai.base.system.entity.UserEntity;
import com.huicai.config.security.LoginUser;
import com.huicai.sme.salary.service.SalaryBillService;
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

@WebMvcTest(com.huicai.sme.salary.controller.SalaryBillController.class)
@AutoConfigureMockMvc(addFilters = false)
class SalaryBillRestContractTest {

    @Autowired private MockMvc mvc;
    @MockBean private SalaryBillService service;
    private static final String BASE = "/api/sme/salary/v1/salary-bills";

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

    @Test @DisplayName("GET — 工资单列表")
    void list() throws Exception {
        when(service.list()).thenReturn(List.of());
        mvc.perform(get(BASE).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test @DisplayName("GET /{id} — 详情（员工级明细 + 个税快照）")
    void detail() throws Exception {
        when(service.detail(5L)).thenReturn(new com.huicai.sme.salary.vo.SalaryBillDetailVO());
        mvc.perform(get(BASE + "/5").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(service).detail(5L);
    }

    @Test @DisplayName("GET /{id}/items — 原始明细（编辑草稿回显）")
    void items() throws Exception {
        when(service.items(5L)).thenReturn(List.of());
        mvc.perform(get(BASE + "/5/items").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(service).items(5L);
    }

    @Test @DisplayName("POST — 保存工资单（合法体，service 返回 billId）")
    void save() throws Exception {
        when(service.save(any(), anyLong())).thenReturn(777L);
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(
                        "{\"period\":\"202601\",\"remark\":\"11月工资\","
                                + "\"items\":[{\"employeeId\":1,\"itemId\":2,\"amount\":30000}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(777));
    }

    @Test @DisplayName("POST — 空 body 必须校验 400 且 service 零调用（负向断言）")
    void save_missingFields_is400() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).save(any(), anyLong());
    }

    @Test @DisplayName("POST — period 非 yyyyMM 必须 400 且 service 零调用")
    void save_badPeriod_is400() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(
                        "{\"period\":\"2026-01\",\"items\":[{\"employeeId\":1,\"itemId\":2,\"amount\":1}]}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).save(any(), anyLong());
    }

    @Test @DisplayName("POST /{id}/confirm — 确认计提")
    void confirm() throws Exception {
        mvc.perform(post(BASE + "/9/confirm").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(service).confirm(eq(9L), anyLong());
    }

    @Test @DisplayName("POST /{id}/pay — 发放")
    void pay() throws Exception {
        mvc.perform(post(BASE + "/9/pay").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(service).pay(eq(9L), anyLong());
    }
}
