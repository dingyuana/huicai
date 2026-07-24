package com.huicai.agency.tenant.controller;

import com.huicai.agency.tenant.entity.EnterpriseEntity;
import com.huicai.agency.tenant.mapper.AgencyEnterpriseMapper;
import com.huicai.agency.tenant.mapper.EnterpriseMapper;
import com.huicai.agency.tenant.service.EnterpriseService;
import com.huicai.agency.user.entity.AgencyUserEntity;
import com.huicai.agency.user.mapper.AgencyUserEnterpriseMapper;
import com.huicai.agency.user.mapper.AgencyUserMapper;
import com.huicai.base.system.entity.MenuEntity;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.base.system.mapper.MenuMapper;
import com.huicai.base.system.mapper.RoleMenuMapper;
import com.huicai.base.system.mapper.UserMapper;
import com.huicai.base.system.mapper.UserRoleMapper;
import com.huicai.base.system.service.MenuService;
import com.huicai.base.system.service.impl.UserDetailsServiceImpl;
import com.huicai.config.security.JwtProvider;
import com.huicai.config.security.LoginUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * EnterpriseController 单元测试 — 企业切换与角色权限分流
 */
@SpringBootTest
@AutoConfigureMockMvc
class EnterpriseControllerTest {

    private static final String TOKEN_ADMIN = "token.admin";
    private static final String TOKEN_ACCOUNTANT = "token.accountant";
    private static final String TOKEN_REVIEWER = "token.reviewer";
    private static final String TOKEN_ASSISTANT = "token.assistant";
    private static final String TOKEN_ENTERPRISE = "token.enterprise";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AgencyEnterpriseMapper agencyEnterpriseMapper;

    @MockBean
    private EnterpriseMapper enterpriseMapper;

    @MockBean
    private EnterpriseService enterpriseService;

    @MockBean
    private AgencyUserMapper agencyUserMapper;

    @MockBean
    private AgencyUserEnterpriseMapper agencyUserEnterpriseMapper;

    @MockBean
    private JwtProvider jwtProvider;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    @SuppressWarnings("rawtypes")
    private ValueOperations valueOperations;

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

    @SuppressWarnings("unchecked")
    private void stubToken(String token, Long userId, String username, String userType,
                           Long agencyId, String agencyRole) {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(eq("token:blacklist:" + token))).thenReturn(null);
        when(jwtProvider.validateToken(eq(token))).thenReturn(true);
        when(jwtProvider.getUsernameFromToken(eq(token))).thenReturn(username);
        when(jwtProvider.getUserIdFromToken(eq(token))).thenReturn(userId);
        when(jwtProvider.getEnterpriseIdFromToken(eq(token))).thenReturn(null);
        when(jwtProvider.getAgencyIdFromToken(eq(token))).thenReturn(agencyId);
        when(jwtProvider.getUserTypeFromToken(eq(token))).thenReturn(userType);
        when(jwtProvider.getAgencyRoleFromToken(eq(token))).thenReturn(agencyRole);

        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername(username);
        user.setPassword("$2a$10$encoded");
        user.setUserType(userType);
        user.setAgencyId(agencyId);
        user.setAgencyRole(agencyRole);
        user.setStatus("ACTIVE");
        user.setDeleted(0);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleMapper.getRoleIdsByUserId(eq(userId))).thenReturn(List.of(1L));
        when(roleMenuMapper.getMenuIdsByRoleId(eq(1L))).thenReturn(List.of());
        when(menuMapper.selectBatchIds(anyList())).thenReturn(List.<MenuEntity>of());

        // Mock UserDetailsService for JWT filter
        LoginUser loginUser = new LoginUser(user, List.of(), null, agencyId, userType, agencyRole);
        when(userDetailsService.loadUserByUsername(eq(username))).thenReturn(loginUser);
    }

    private void stubAdmin() {
        stubToken(TOKEN_ADMIN, 1L, "admin", "AGENCY", 1L, "AGENCY_ADMIN");
    }

    private void stubAccountant() {
        stubToken(TOKEN_ACCOUNTANT, 2L, "accountant", "AGENCY", 1L, "ACCOUNTANT");
    }

    private void stubReviewer() {
        stubToken(TOKEN_REVIEWER, 3L, "reviewer", "AGENCY", 1L, "REVIEWER");
    }

    private void stubAssistant() {
        stubToken(TOKEN_ASSISTANT, 4L, "assistant", "AGENCY", 1L, "ASSISTANT");
    }

    private void stubEnterprise() {
        stubToken(TOKEN_ENTERPRISE, 5L, "enterprise", "ENTERPRISE", null, null);
    }

    @Test
    @DisplayName("场景10: ACCOUNTANT 切换到分配的企业成功")
    void testAccountantSwitchOwn() throws Exception {
        stubAccountant();

        AgencyUserEntity agencyUser = new AgencyUserEntity();
        agencyUser.setId(10L);
        agencyUser.setUserId(2L);
        when(agencyUserMapper.selectOne(any())).thenReturn(agencyUser);
        when(agencyUserEnterpriseMapper.getEnterpriseIdsByAgencyUserId(10L)).thenReturn(List.of(1L));

        EnterpriseEntity enterprise = new EnterpriseEntity();
        enterprise.setId(1L);
        enterprise.setEnterpriseName("测试企业");
        enterprise.setSeedDataDone(true);
        enterprise.setDeleted(0);
        when(enterpriseMapper.selectById(1L)).thenReturn(enterprise);

        mvc.perform(post("/api/v1/enterprise/switch")
                        .header("Authorization", "Bearer " + TOKEN_ACCOUNTANT)
                        .param("enterpriseId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.enterpriseId").value(1))
                .andExpect(jsonPath("$.data.enterpriseName").value("测试企业"));
    }

    @Test
    @DisplayName("场景11: ACCOUNTANT 不能切换到未分配的企业")
    void testAccountantSwitchBlocked() throws Exception {
        stubAccountant();

        AgencyUserEntity agencyUser = new AgencyUserEntity();
        agencyUser.setId(10L);
        agencyUser.setUserId(2L);
        when(agencyUserMapper.selectOne(any())).thenReturn(agencyUser);
        when(agencyUserEnterpriseMapper.getEnterpriseIdsByAgencyUserId(10L)).thenReturn(List.of(1L));

        mvc.perform(post("/api/v1/enterprise/switch")
                        .header("Authorization", "Bearer " + TOKEN_ACCOUNTANT)
                        .param("enterpriseId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("场景12: AGENCY_ADMIN 可以切换到任意绑定的企业")
    void testAgencyAdminSwitchAny() throws Exception {
        stubAdmin();

        when(agencyEnterpriseMapper.getEnterpriseIdsByAgencyId(1L)).thenReturn(List.of(1L, 2L, 3L));

        EnterpriseEntity enterprise = new EnterpriseEntity();
        enterprise.setId(3L);
        enterprise.setEnterpriseName("企业C");
        enterprise.setSeedDataDone(true);
        enterprise.setDeleted(0);
        when(enterpriseMapper.selectById(3L)).thenReturn(enterprise);

        mvc.perform(post("/api/v1/enterprise/switch")
                        .header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .param("enterpriseId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.enterpriseId").value(3));
    }

    @Test
    @DisplayName("场景17: REVIEWER 可以切换到任意绑定的企业")
    void testReviewerCanSwitch() throws Exception {
        stubReviewer();

        when(agencyEnterpriseMapper.getEnterpriseIdsByAgencyId(1L)).thenReturn(List.of(1L, 2L));

        EnterpriseEntity enterprise = new EnterpriseEntity();
        enterprise.setId(2L);
        enterprise.setEnterpriseName("企业B");
        enterprise.setSeedDataDone(true);
        enterprise.setDeleted(0);
        when(enterpriseMapper.selectById(2L)).thenReturn(enterprise);

        mvc.perform(post("/api/v1/enterprise/switch")
                        .header("Authorization", "Bearer " + TOKEN_REVIEWER)
                        .param("enterpriseId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.enterpriseId").value(2));
    }

    @Test
    @DisplayName("场景13: ASSISTANT 只能切换到分配的企业")
    void testAssistantSwitchOwn() throws Exception {
        stubAssistant();

        AgencyUserEntity agencyUser = new AgencyUserEntity();
        agencyUser.setId(20L);
        agencyUser.setUserId(4L);
        when(agencyUserMapper.selectOne(any())).thenReturn(agencyUser);
        when(agencyUserEnterpriseMapper.getEnterpriseIdsByAgencyUserId(20L)).thenReturn(List.of(1L));

        EnterpriseEntity enterprise = new EnterpriseEntity();
        enterprise.setId(1L);
        enterprise.setEnterpriseName("企业A");
        enterprise.setSeedDataDone(true);
        enterprise.setDeleted(0);
        when(enterpriseMapper.selectById(1L)).thenReturn(enterprise);

        mvc.perform(post("/api/v1/enterprise/switch")
                        .header("Authorization", "Bearer " + TOKEN_ASSISTANT)
                        .param("enterpriseId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.enterpriseId").value(1));
    }

    @Test
    @DisplayName("场景6: ENTERPRISE 用户不能切换企业")
    void testEnterpriseUserCannotSwitch() throws Exception {
        stubEnterprise();

        mvc.perform(post("/api/v1/enterprise/switch")
                        .header("Authorization", "Bearer " + TOKEN_ENTERPRISE)
                        .param("enterpriseId", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("切换企业_企业不存在_返回404")
    void testSwitchEnterpriseNotFound() throws Exception {
        stubAdmin();

        when(agencyEnterpriseMapper.getEnterpriseIdsByAgencyId(1L)).thenReturn(List.of(1L));
        when(enterpriseMapper.selectById(1L)).thenReturn(null);

        mvc.perform(post("/api/v1/enterprise/switch")
                        .header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .param("enterpriseId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }
}