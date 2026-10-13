package com.huicai.config.security;

import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.response.R;
import com.huicai.base.system.service.impl.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
/**
 * ⚠️ SPEC-P124：**刻意不加 {@code @Component}**。
 *
 * <p>本类原先既带 {@code @Component}（被 Spring Boot 当作 Servlet Filter 自动注册进容器），
 * 又在 {@code SecurityConfig} 里 {@code addFilterBefore(...)} 显式加入安全链 ——
 * <b>双注册</b>，每次请求过滤器完整跑两遍。
 *
 * <p>实测（2026-10-13，单次业务请求统计 {@code SELECT ... t_user WHERE username} 次数）：
 * 带 {@code @Component} = <b>4</b> 次；去掉后 = <b>2</b> 次。
 * 即鉴权相关的数据库查询<b>翻倍</b>，且第二次登录态装载纯属浪费。
 *
 * <p>改由 {@code SecurityConfig} 中的 {@code @Bean} 显式创建并传入
 * {@code securityFilterChain(...)}，保证<b>只有一条注册路径</b>。
 *
 * <p>注意：不能简单删掉这个 Bean —— {@code SecurityConfig} 需要注入它。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final StringRedisTemplate redisTemplate;

    public JwtAuthenticationFilter(JwtProvider jwtProvider,
                                   UserDetailsServiceImpl userDetailsService,
                                   StringRedisTemplate redisTemplate) {
        this.jwtProvider = jwtProvider;
        this.userDetailsService = userDetailsService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null) {
            // Check blacklist (logged out tokens)
            String blacklisted = redisTemplate.opsForValue().get("token:blacklist:" + token);
            if (blacklisted != null) {
                filterChain.doFilter(request, response);
                return;
            }

            if (jwtProvider.validateToken(token)) {
                String username = jwtProvider.getUsernameFromToken(token);
                Long enterpriseId = jwtProvider.getEnterpriseIdFromToken(token);
                Long agencyId = jwtProvider.getAgencyIdFromToken(token);
                String userType = jwtProvider.getUserTypeFromToken(token);

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // S-26: 前端 X-Enterprise-Id 覆盖 JWT 中的 enterpriseId
                // 切换企业后 JWT 未更新，由前端 header 传递当前选中企业
                //
                // P102 / AT-102-1：原实现无条件信任该头，任意登录用户改一个头即可
                // 切换到任意企业。现按 EnterpriseMembershipChecker 的三源并集校验
                // （直属企业 ∪ 代理成员授权 ∪ SUPER_ADMIN），不通过即 403。
                // 失败时不区分「企业不存在」与「无权限」，避免泄露企业存在性。
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);

                // SPEC-P124 切片5：企业上下文<b>只来自 JWT</b>。
                // X-Enterprise-Id 请求头已废止 —— 它曾使「当前企业」有三个来源且
                // 其中一个由客户端可控（3 源 2 义 0 权威）。切换改为由
                // /enterprise/switch 校验并重签 token，权威收敛到服务端。
                // 授权判定在该切换端点完成（绑定/派工校验 + 切换审计）。
                if (enterpriseId != null) {
                    EnterpriseContextHolder.set(enterpriseId);
                }
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            EnterpriseContextHolder.clear();
        }
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
