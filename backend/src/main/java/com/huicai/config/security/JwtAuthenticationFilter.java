package com.huicai.config.security;

import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.response.R;
import com.huicai.base.system.service.EnterpriseSwitchAuditService;
import com.huicai.common.security.EnterpriseMembershipChecker;
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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final StringRedisTemplate redisTemplate;

    /**
     * P102 新增的两个依赖用 {@code @Lazy} 延迟解析：
     * {@code @WebMvcTest} 切片会把 {@code Filter} 类型 Bean 实例化，却不扫描
     * {@code @Component/@Service}，若构造期强制解析会导致 12 个契约测试类
     * 上下文加载失败（NoSuchBeanDefinitionException）。切片本身
     * {@code addFilters = false}，过滤器不会执行，故永不触发解析。
     * 生产上下文中二者必然存在，且缺失时表现为异常而非静默跳过（fail-closed）。
     */
    private final EnterpriseMembershipChecker enterpriseMembershipChecker;
    private final EnterpriseSwitchAuditService auditService;

    public JwtAuthenticationFilter(JwtProvider jwtProvider,
                                   UserDetailsServiceImpl userDetailsService,
                                   StringRedisTemplate redisTemplate,
                                   @Lazy EnterpriseMembershipChecker enterpriseMembershipChecker,
                                   @Lazy EnterpriseSwitchAuditService auditService) {
        this.jwtProvider = jwtProvider;
        this.userDetailsService = userDetailsService;
        this.redisTemplate = redisTemplate;
        this.enterpriseMembershipChecker = enterpriseMembershipChecker;
        this.auditService = auditService;
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
                String xEnterpriseId = request.getHeader("X-Enterprise-Id");
                if (xEnterpriseId != null) {
                    Long requested;
                    try {
                        requested = Long.parseLong(xEnterpriseId);
                    } catch (NumberFormatException e) {
                        requested = null;
                    }
                    if (requested != null && !requested.equals(enterpriseId)) {
                        LoginUser loginUser = (LoginUser) userDetails;
                        boolean allowed = enterpriseMembershipChecker.isMember(
                                loginUser.getUserId(), loginUser.getEnterpriseId(),
                                loginUser.getUserType(), requested);
                        if (!allowed) {
                            log.warn("越权切换企业被拒: user={}, target={}", username, requested);
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    "{\"code\":403,\"msg\":\"无权访问该企业\",\"data\":null}");
                            return;
                        }
                        auditService.recordEnterpriseSwitch(loginUser.getUserId(),
                                enterpriseId, requested);
                    }
                    if (requested != null) {
                        enterpriseId = requested;
                    }
                }

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
