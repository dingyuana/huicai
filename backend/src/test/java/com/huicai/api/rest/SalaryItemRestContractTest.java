/**
 * SalaryItemController 契约测试 (L1 MockMvc / @WebMvcTest)
 *
 * SPEC-P143：薪资项目配置端点 —— HTTP 契约与入参校验面。
 * 权限面由 PermissionManifestGuardRealDBTest 守卫（manifest 登记行 + V197 授权）。
 */
package com.huicai.api.rest;

import com.huicai.base.system.entity.UserEntity;
import com.huicai.config.security.LoginUser;
import com.huicai.sme.salary.service.SalaryItemService;
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

@WebMvcTest(com.huicai.sme.salary.controller.SalaryItemController.class)
@AutoConfigureMockMvc(addFilters = false)
class SalaryItemRestContractTest {

    @Autowired private MockMvc mvc;
    @MockBean private SalaryItemService service;
    private static final String BASE = "/api/sme/salary/v1/salary-items";

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

    @Test @DisplayName("GET — 薪资项目列表")
    void list() throws Exception {
        when(service.list()).thenReturn(List.of());
        mvc.perform(get(BASE).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test @DisplayName("POST — 新建项目（合法体，service 返回 id）")
    void create() throws Exception {
        when(service.create(any(), anyLong())).thenReturn(66L);
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(
                        "{\"itemCode\":\"BASIC\",\"itemName\":\"基本工资\","
                                + "\"category\":\"EARN\",\"isTaxable\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(66));
    }

    @Test @DisplayName("POST — 缺必填字段必须 400 且 service 零调用（负向断言）")
    void create_missingRequiredFields_is400() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).create(any(), anyLong());
    }

    @Test @DisplayName("POST — category 非四类枚举必须 400 且 service 零调用")
    void create_badCategory_is400() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(
                        "{\"itemCode\":\"X\",\"itemName\":\"X\",\"category\":\"SALARY\"}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).create(any(), anyLong());
    }

    @Test @DisplayName("PUT /{id} — 修改项目")
    void update() throws Exception {
        mvc.perform(put(BASE + "/7").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"itemCode\":\"BASIC\",\"itemName\":\"基本工资\","
                                + "\"category\":\"EARN\",\"isTaxable\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(service).update(eq(7L), any(), anyLong());
    }

    @Test @DisplayName("DELETE /{id} — 软删项目")
    void deleteItem() throws Exception {
        mvc.perform(delete(BASE + "/7").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(service).delete(7L, 1L);
    }
}
