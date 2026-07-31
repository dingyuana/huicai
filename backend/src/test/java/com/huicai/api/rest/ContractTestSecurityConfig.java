package com.huicai.api.rest;

import com.huicai.base.system.entity.UserEntity;
import com.huicai.config.security.JwtProvider;
import com.huicai.config.security.LoginUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

import static org.mockito.Mockito.mock;

/**
 * 契约测试安全配置 — 为所有请求设置 Mock 安全上下文
 * 并提供 Mock JwtProvider 避免 JwtAuthenticationFilter 加载失败
 */
@TestConfiguration
public class ContractTestSecurityConfig {

    @Bean
    public OncePerRequestFilter mockSecurityFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain)
                    throws ServletException, IOException {
                UserEntity user = new UserEntity();
                user.setId(1L);
                user.setUsername("admin");
                user.setPassword("encoded");
                user.setUserType("AGENCY");
                user.setAgencyId(1L);
                user.setAgencyRole("AGENCY_ADMIN");
                user.setStatus("ACTIVE");
                user.setDeleted(0);

                LoginUser loginUser = new LoginUser(user, List.of(), null, 1L, "AGENCY", "AGENCY_ADMIN");
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
                filterChain.doFilter(request, response);
            }
        };
    }

    @Bean
    public JwtProvider mockJwtProvider() {
        return mock(JwtProvider.class);
    }
}