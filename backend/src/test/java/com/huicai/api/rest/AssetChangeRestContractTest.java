/**
 * AssetChangeController 契约测试 (L1 MockMvc / @WebMvcTest)
 *
 * SPEC-P144（D-4）：资产变动端点 —— HTTP 契约与入参校验面。
 * 权限面由 PermissionManifestGuardRealDBTest 守卫（manifest 两行登记 + V196 授权）。
 */
package com.huicai.api.rest;

import com.huicai.base.system.entity.UserEntity;
import com.huicai.config.security.LoginUser;
import com.huicai.sme.asset.dto.AssetChangeCreateDTO;
import com.huicai.sme.asset.service.AssetChangeService;
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

@WebMvcTest(com.huicai.sme.asset.controller.AssetChangeController.class)
@AutoConfigureMockMvc(addFilters = false)
class AssetChangeRestContractTest {

    @Autowired private MockMvc mvc;
    @MockBean private AssetChangeService service;
    private static final String BASE = "/api/sme/asset/v1/asset-changes";

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

    @Test @DisplayName("POST — 登记变动（合法体，service 返回变动 id）")
    void create() throws Exception {
        when(service.create(any(AssetChangeCreateDTO.class), anyLong())).thenReturn(555L);
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(
                        "{\"assetId\":11,\"changeType\":\"VALUE_ADJUST\","
                                + "\"changeDate\":\"2026-11-01\",\"amount\":10000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(555));
    }

    @Test @DisplayName("POST — 缺必填字段必须 400 且 service 零调用（负向断言）")
    void create_missingRequiredFields_is400() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).create(any(), anyLong());
    }

    @Test @DisplayName("POST — changeType=DEPRECIATION（计提专用类型）必须 400 且 service 零调用")
    void create_depreciationType_is400() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(
                        "{\"assetId\":11,\"changeType\":\"DEPRECIATION\","
                                + "\"changeDate\":\"2026-11-01\",\"amount\":1000}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).create(any(), anyLong());
    }

    @Test @DisplayName("GET ?assetId= — 变动流水（含凭证号回显）")
    void listByAsset() throws Exception {
        when(service.listByAsset(11L)).thenReturn(List.of());
        mvc.perform(get(BASE).param("assetId", "11").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }
}
