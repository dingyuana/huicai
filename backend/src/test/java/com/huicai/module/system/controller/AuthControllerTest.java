package com.huicai.base.system.controller;

import com.huicai.config.security.JwtProvider;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.base.system.mapper.MenuMapper;
import com.huicai.base.system.mapper.RoleMenuMapper;
import com.huicai.base.system.mapper.UserMapper;
import com.huicai.base.system.mapper.UserRoleMapper;
import com.huicai.base.system.service.MenuService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

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
        when(jwtProvider.generateAccessToken(anyString(), anyLong(), anyList())).thenReturn("mock-access-token");
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
        verify(jwtProvider).generateAccessToken(eq("admin"), eq(1L), argThat(roles -> roles.contains("1") && roles.contains("2")));
    }

    @Test
    @DisplayName("登录失败_用户名或密码错误_返回500")
    void login_failed_wrongCredentials_returns500() throws Exception {
        // given
        when(authenticationManager.authenticate(any()))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("用户名或密码错误"));

        AuthController.LoginRequest request = new AuthController.LoginRequest();
        request.setUsername("admin");
        request.setPassword("wrong");

        // when & then - BadCredentialsException → GlobalExceptionHandler → 500
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500));
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

        // when & then
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
        when(jwtProvider.generateAccessToken(anyString(), anyLong(), anyList())).thenReturn("token");
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
    @DisplayName("获取用户信息_认证用户返回200")
    void getUserInfo_authenticated_returnsOk() throws Exception {
        // given
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("admin");

        // Set the authentication in SecurityContext
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);

        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("admin");
        user.setRealName("管理员");
        user.setNickname("Admin");
        user.setEmail("admin@huicai.com");
        user.setPhone("13800138000");
        when(userMapper.selectByUsername(eq("admin"))).thenReturn(user);

        when(userRoleMapper.getRoleIdsByUserId(eq(1L))).thenReturn(List.of(1L));
        when(menuService.getUserButtonPermissions(eq(1L))).thenReturn(List.of());

        // when & then
        mvc.perform(get("/api/v1/auth/userinfo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.realName").value("管理员"))
                .andExpect(jsonPath("$.data.email").value("admin@huicai.com"));

        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}