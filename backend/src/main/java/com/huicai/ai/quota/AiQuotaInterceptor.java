package com.huicai.ai.quota;

import com.huicai.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * SPEC-P135 —— AI 配额拦截器。
 *
 * <p><b>只拦写方法</b>：GET 查询不消耗额度（SPEC AT-135-4），
 * 否则前端每刷新一次列表就扣一次。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiQuotaInterceptor implements HandlerInterceptor {

    /**
     * 🔴 <b>可选依赖</b>，与 P133 拦截器同一坑（P133 §8.2 已沉淀）：
     * {@code @WebMvcTest} 是<b>切片上下文</b>，只加载白名单 bean
     * （Controller / WebMvcConfigurer / Filter / <b>HandlerInterceptor</b>…），
     * <b>不扫描普通 @Component</b>。本拦截器恰好在白名单里（它实现了
     * {@code HandlerInterceptor}），若构造器直接要 {@code AiQuotaGuard}，
     * 则<b>每个 @WebMvcTest 用例都会 NoSuchBeanDefinitionException</b>
     * —— 实测一次性挂掉 <b>97 个用例</b>。
     */
    private final ObjectProvider<AiQuotaGuard> guardProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        String method = request.getMethod();
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return true;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
            // 匿名请求交给 SecurityConfig；配额不是认证机制
            return true;
        }

        AiQuotaGuard guard = guardProvider.getIfAvailable();
        if (guard == null) {
            // 切片测试里没有 AiQuotaGuard（它依赖 Redis），此时不安装执行
            return true;
        }

        Set<String> authorities = auth.getAuthorities().stream()
                .map(a -> a.getAuthority()).collect(Collectors.toSet());
        guard.consume(authorities);
        return true;
    }
}