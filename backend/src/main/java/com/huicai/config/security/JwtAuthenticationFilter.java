package com.huicai.config.security;

import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.response.R;
import com.huicai.base.system.service.impl.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final StringRedisTemplate redisTemplate;

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

                // S-26: 前端 X-Enterprise-Id 覆盖 JWT 中的 enterpriseId
                // 切换企业后 JWT 未更新，由前端 header 传递当前选中企业
                String xEnterpriseId = request.getHeader("X-Enterprise-Id");
                if (xEnterpriseId != null) {
                    try {
                        enterpriseId = Long.parseLong(xEnterpriseId);
                    } catch (NumberFormatException ignored) {
                    }
                }

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);

                // S-26: 设置企业上下文（SUPER_ADMIN 随 X-Enterprise-Id 头切换）
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
