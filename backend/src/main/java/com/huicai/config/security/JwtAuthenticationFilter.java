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

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);

                // S-26: 设置企业上下文（SUPER_ADMIN 不设，跳过数据权限拦截）
                if (enterpriseId != null && !"SUPER_ADMIN".equals(userType)) {
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
