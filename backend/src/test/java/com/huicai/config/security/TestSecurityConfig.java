/**
 * 测试环境安全配置 — 放行所有请求
 *
 * <p>在 test profile 下覆盖生产 SecurityConfig，允许 RestAssured 契约测试
 * 无需携带 JWT Token 即可访问 API 端点，专注于 HTTP 契约验证。
 *
 * <p>通过 @Order(0) 确保优先于生产 SecurityConfig 的 SecurityFilterChain 匹配。
 */
package com.huicai.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 注意：使用独立 profile "contract-test" 而非 "test"，
 * 避免影响 TaxApiContractTest 等使用 @WithMockUser 的 MockMvc 测试。
 * TaxRestContractTest 使用 @ActiveProfiles({"test", "contract-test"}) 加载。
 */
@Configuration
@Profile("contract-test")
@EnableWebSecurity
public class TestSecurityConfig {

    @Bean
    @Order(0)
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }
}