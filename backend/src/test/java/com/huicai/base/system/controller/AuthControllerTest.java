package com.huicai.base.system.controller;

import com.huicai.base.system.entity.MenuEntity;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.base.system.mapper.MenuMapper;
import com.huicai.base.system.mapper.RoleMenuMapper;
import com.huicai.base.system.mapper.UserMapper;
import com.huicai.base.system.mapper.UserRoleMapper;
import com.huicai.base.system.service.MenuService;
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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController 集成测试.
 *
 * <p>安全过滤链保持启用（不使用 addFilters=false），确保 JWT 认证与 RBAC
 * 拦截真实生效。受保护接口必须携带有效 Bearer Token 才能访问，
 * 缺失或过期 Token 将被 {@code JwtAuthEntryPoint} 拦截返回 401。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final String EXPIRED_TOKEN = "expired.jwt.token";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private AuthenticationManager authenticationManager;

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

    @AfterEach
    void clearSecurityContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    /**
     * 构造一个启用的 UserEntity，供 UserDetailsServiceImpl 加载。
     */
    private UserEntity buildActiveUser() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("$2a$10$encodedpasswordhashvalue");
        user.setRealName("管理员");
        user.setNickname("Admin");
        user.setEmail("admin@huicai.com");
        user.setPhone("13800138000");
        user.setStatus("ACTIVE");
        user.setDeleted(0);
        return user;
    }

    /**
     * 配置 JWT 过滤链的 mock：Token 有效、未加黑名单、用户存在。
     * JwtAuthenticationFilter 会依次调用 redisTemplate.opsForValue().get()、
     * jwtProvider.validateToken()、jwtProvider.getUsernameFromToken()、
     * userDetailsService.loadUserByUsername()。
     */
    @SuppressWarnings("unchecked")
    private void stubValidToken(String token, String username, UserEntity user) {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(eq("token:blacklist:" + token))).thenReturn(null);
        when(jwtProvider.validateToken(eq(token))).thenReturn(true);
        when(jwtProvider.getUsernameFromToken(eq(token))).thenReturn(username);
        // UserDetailsServiceImpl.loadUserByUsername 调用链
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userRoleMapper.getRoleIdsByUserId(eq(1L))).thenReturn(List.of(1L));
        when(roleMenuMapper.getMenuIdsByRoleId(eq(1L))).thenReturn(List.of());
        when(menuMapper.selectBatchIds(anyList())).thenReturn(List.<MenuEntity>of());
    }

    @Test
    @DisplayName("登录成功_返回200和Token信息")
    void login_success_returnsToken() throws Exception {
        // given
        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(authentication.getName()).thenReturn("admin");

        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("admin");
        user.setRealName("管理员");
        when(userMapper.selectByUsername(eq("admin"))).thenReturn(user);

        when(userRoleMapper.getRoleIdsByUserId(eq(1L))).thenReturn(List.of(1L, 2L));
        when(menuService.getUserButtonPermissions(eq(1L))).thenReturn(List.of("system:user:list", "system:role:list"));
        when(jwtProvider.generateAccessToken(anyString(), anyLong(), anyList(), any(), any(), any())).thenReturn("mock-access-token");
        when(jwtProvider.generateRefreshToken(anyString())).thenReturn("mock-refresh-token");

        AuthController.LoginRequest request = new AuthController.LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");

        // when & then
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("mock-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("mock-refresh-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.userInfo.username").value("admin"))
                .andExpect(jsonPath("$.data.userInfo.id").value(1));

        verify(authenticationManager).authenticate(any());
        verify(userMapper).selectByUsername(eq("admin"));
        verify(jwtProvider).generateAccessToken(eq("admin"), eq(1L), argThat(roles -> roles.contains("1") && roles.contains("2")), any(), any(), any());
    }

    @Test
    @DisplayName("登录失败_用户名或密码错误_返回400")
    void login_failed_wrongCredentials_returns400() throws Exception {
        // given
        when(authenticationManager.authenticate(any()))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("用户名或密码错误"));

        AuthController.LoginRequest request = new AuthController.LoginRequest();
        request.setUsername("admin");
        request.setPassword("wrong");

        // when & then - BadCredentialsException → GlobalExceptionHandler → 400
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("登录失败_用户不存在_返回500")
    void login_failed_userNotFound_returnsFail() throws Exception {
        // given
        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "ghost", "any", java.util.Collections.emptyList());
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(userMapper.selectByUsername(eq("ghost"))).thenReturn(null);

        AuthController.LoginRequest request = new AuthController.LoginRequest();
        request.setUsername("ghost");
        request.setPassword("any");

        // when & then - NullPointerException → GlobalExceptionHandler → 500
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    @DisplayName("登录失败_密码为空_请求体正确序列化")
    void login_missingPassword_returns500() throws Exception {
        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "admin", "any", java.util.Collections.emptyList());
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("admin");
        when(userMapper.selectByUsername(eq("admin"))).thenReturn(user);
        when(userRoleMapper.getRoleIdsByUserId(eq(1L))).thenReturn(java.util.Collections.emptyList());
        when(menuService.getUserButtonPermissions(eq(1L))).thenReturn(java.util.Collections.emptyList());
        when(jwtProvider.generateAccessToken(anyString(), anyLong(), anyList(), any(), any(), any())).thenReturn("token");
        when(jwtProvider.generateRefreshToken(anyString())).thenReturn("refresh");

        AuthController.LoginRequest request = new AuthController.LoginRequest();
        request.setUsername("admin");
        request.setPassword("");

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("获取用户信息_无Token_返回401")
    void getUserInfo_noToken_returns401() throws Exception {
        // 不携带 Authorization 头，JwtAuthenticationFilter 不设置认证，
        // JwtAuthEntryPoint 拦截返回 401
        mvc.perform(get("/api/v1/auth/userinfo"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("未授权，请先登录"));
    }

    @Test
    @DisplayName("获取用户信息_有效Token_返回200")
    void getUserInfo_validToken_returns200() throws Exception {
        // given
        UserEntity user = buildActiveUser();
        stubValidToken(VALID_TOKEN, "admin", user);

        when(userMapper.selectByUsername(eq("admin"))).thenReturn(user);
        when(userRoleMapper.getRoleIdsByUserId(eq(1L))).thenReturn(List.of(1L));
        when(menuService.getUserButtonPermissions(eq(1L))).thenReturn(List.of());

        // when & then
        mvc.perform(get("/api/v1/auth/userinfo")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.realName").value("管理员"))
                .andExpect(jsonPath("$.data.email").value("admin@huicai.com"));

        // 负向断言：过期 Token 的桩不应被调用
        verify(jwtProvider, never()).validateToken(eq(EXPIRED_TOKEN));
    }

    @Test
    @DisplayName("获取用户信息_过期Token_返回401")
    void getUserInfo_expiredToken_returns401() throws Exception {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(eq("token:blacklist:" + EXPIRED_TOKEN))).thenReturn(null);
        // jwtProvider.validateToken 返回 false → 过滤链不设置认证 → 401
        when(jwtProvider.validateToken(eq(EXPIRED_TOKEN))).thenReturn(false);

        // when & then
        mvc.perform(get("/api/v1/auth/userinfo")
                        .header("Authorization", "Bearer " + EXPIRED_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("未授权，请先登录"));

        // 负向断言：Token 验证失败后不应继续解析用户名，也不应查询用户
        verify(jwtProvider, never()).getUsernameFromToken(anyString());
        verify(userMapper, never()).selectOne(any());
        verify(userMapper, never()).selectByUsername(anyString());
    }
}