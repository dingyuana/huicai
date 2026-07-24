package com.huicai.agency.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.agency.user.dto.AgencyUserCreateDTO;
import com.huicai.agency.user.dto.AgencyUserVO;
import com.huicai.agency.user.service.AgencyUserService;
import com.huicai.base.system.entity.MenuEntity;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.base.system.mapper.MenuMapper;
import com.huicai.base.system.mapper.RoleMenuMapper;
import com.huicai.base.system.mapper.UserMapper;
import com.huicai.base.system.mapper.UserRoleMapper;
import com.huicai.base.system.service.MenuService;
import com.huicai.common.exception.BusinessException;
import com.huicai.config.security.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AgencyUserController 单元测试 — 代理用户管理 API
 */
@SpringBootTest
@AutoConfigureMockMvc
class AgencyUserControllerTest {

    private static final String VALID_TOKEN = "valid.jwt.token";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private AgencyUserService agencyUserService;

    @MockBean
    private JwtProvider jwtProvider;

    @MockBean
    private UserMapper userMapper;

    @MockBean
    private UserRoleMapper userRoleMapper;

    @MockBean
    private RoleMenuMapper roleMenuMapper;

    @MockBean
    private MenuMapper menuMapper;

    @MockBean
    private MenuService menuService;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    @SuppressWarnings("rawtypes")
    private ValueOperations valueOperations;

    @SuppressWarnings("unchecked")
    private void stubValidToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(eq("token:blacklist:" + VALID_TOKEN))).thenReturn(null);
        when(jwtProvider.validateToken(eq(VALID_TOKEN))).thenReturn(true);
        when(jwtProvider.getUsernameFromToken(eq(VALID_TOKEN))).thenReturn("admin");
        when(jwtProvider.getUserIdFromToken(eq(VALID_TOKEN))).thenReturn(1L);
        when(jwtProvider.getEnterpriseIdFromToken(eq(VALID_TOKEN))).thenReturn(null);
        when(jwtProvider.getAgencyIdFromToken(eq(VALID_TOKEN))).thenReturn(1L);
        when(jwtProvider.getUserTypeFromToken(eq(VALID_TOKEN))).thenReturn("AGENCY");
        when(jwtProvider.getAgencyRoleFromToken(eq(VALID_TOKEN))).thenReturn("AGENCY_ADMIN");

        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("$2a$10$encoded");
        user.setUserType("AGENCY");
        user.setAgencyId(1L);
        user.setAgencyRole("AGENCY_ADMIN");
        user.setStatus("ACTIVE");
        user.setDeleted(0);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleMapper.getRoleIdsByUserId(eq(1L))).thenReturn(List.of(1L));
        when(roleMenuMapper.getMenuIdsByRoleId(eq(1L))).thenReturn(List.of());
        when(menuMapper.selectBatchIds(anyList())).thenReturn(List.<MenuEntity>of());
    }

    @Test
    @DisplayName("场景9: 创建会计用户成功")
    void testCreateUser() throws Exception {
        stubValidToken();

        AgencyUserCreateDTO dto = new AgencyUserCreateDTO();
        dto.setUsername("accountant01");
        dto.setPassword("pass123");
        dto.setRealName("张会计");
        dto.setAgencyRole("ACCOUNTANT");
        dto.setAgencyId(1L);

        AgencyUserVO vo = new AgencyUserVO();
        vo.setId(1L);
        vo.setAgencyId(1L);
        vo.setUserId(10L);
        vo.setUsername("accountant01");
        vo.setRealName("张会计");
        vo.setAgencyRole("ACCOUNTANT");
        vo.setStatus("ACTIVE");

        when(agencyUserService.create(any(AgencyUserCreateDTO.class))).thenReturn(vo);

        mvc.perform(post("/api/v1/agency/users")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("accountant01"))
                .andExpect(jsonPath("$.data.realName").value("张会计"))
                .andExpect(jsonPath("$.data.agencyRole").value("ACCOUNTANT"));

        verify(agencyUserService).create(any(AgencyUserCreateDTO.class));
    }

    @Test
    @DisplayName("创建用户_参数校验失败_用户名空")
    void testCreateUserValidationFails() throws Exception {
        stubValidToken();

        AgencyUserCreateDTO dto = new AgencyUserCreateDTO();
        dto.setPassword("pass123");
        dto.setRealName("张会计");
        dto.setAgencyRole("ACCOUNTANT");
        dto.setAgencyId(1L);

        mvc.perform(post("/api/v1/agency/users")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("场景16: 暂停代理用户")
    void testSuspendUser() throws Exception {
        stubValidToken();
        doNothing().when(agencyUserService).suspend(1L);

        mvc.perform(post("/api/v1/agency/users/{id}/suspend", 1L)
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(agencyUserService).suspend(1L);
    }

    @Test
    @DisplayName("暂停用户_用户不存在_返回404")
    void testSuspendUserNotFound() throws Exception {
        stubValidToken();
        doThrow(BusinessException.notFound("代理用户不存在"))
                .when(agencyUserService).suspend(99L);

        mvc.perform(post("/api/v1/agency/users/{id}/suspend", 99L)
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("恢复暂停用户")
    void testReactivateUser() throws Exception {
        stubValidToken();
        doNothing().when(agencyUserService).reactivate(1L);

        mvc.perform(post("/api/v1/agency/users/{id}/reactivate", 1L)
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(agencyUserService).reactivate(1L);
    }

    @Test
    @DisplayName("终止暂停用户")
    void testTerminateUser() throws Exception {
        stubValidToken();
        doNothing().when(agencyUserService).terminate(1L);

        mvc.perform(post("/api/v1/agency/users/{id}/terminate", 1L)
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(agencyUserService).terminate(1L);
    }

    @Test
    @DisplayName("获取代理用户详情")
    void testGetById() throws Exception {
        stubValidToken();

        AgencyUserVO vo = new AgencyUserVO();
        vo.setId(1L);
        vo.setAgencyId(1L);
        vo.setUserId(10L);
        vo.setUsername("accountant01");
        vo.setRealName("张会计");
        vo.setAgencyRole("ACCOUNTANT");
        vo.setStatus("ACTIVE");

        when(agencyUserService.getById(1L)).thenReturn(vo);

        mvc.perform(get("/api/v1/agency/users/{id}", 1L)
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("accountant01"));
    }
}