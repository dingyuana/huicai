package com.huicai.common.security;

import com.huicai.common.response.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SPEC-P133 —— 端点权限强制拦截器。
 *
 * <p>本拦截器让<b>权限清单成为执行点</b>，而不是只当文档：新增端点只需在清单登记，
 * 无需回到 543 个方法里逐个加注解（§4.5 第 43 条：逐点加守卫必漏）。</p>
 *
 * <p>🔴 <b>批次 2 阶段 = warn-only（D-1 裁定）</b>：
 * <ul>
 *   <li><b>清单命中且持有权限</b> ⇒ 放行</li>
 *   <li><b>清单命中但缺权限</b> ⇒ <b>403</b>。这与这 19 个端点已有的
 *       {@code @PreAuthorize} <b>行为一致</b>，故本阶段<b>不改变任何现有行为</b>
 *       （SPEC §6 批次 2 风险登记为「低；行为不变」）。</li>
 *   <li><b>清单未命中</b> ⇒ <b>放行 + 告警</b>。此刻放行是刻意的：
 *       存量 288 个写端点尚未登记，若此刻 fail-closed 则<b>全站功能一夜全废</b>。
 *       缺口由守卫 G2 的<b>棘轮</b>盯住（只许减不许增）。</li>
 * </ul>
 *
 * <p>⚠️ <b>不得对外宣称「已完成权限管控」</b>：批次 3 补清单、批次 4 切 fail-closed
 * 之前，越权仍然畅通。</p>
 */
@Slf4j
@org.springframework.stereotype.Component
public class PermissionEnforcementInterceptor implements HandlerInterceptor {

    /**
     * 🔴 用 {@link ObjectProvider} 而非直接依赖：{@code @WebMvcTest} 是切片上下文，
     *    <b>不扫描普通 @Component</b> ⇒ 清单在切片里不存在。
     *    若构造器直接要清单，所有 {@code @WebMvcTest} 用例都会
     *    {@code NoSuchBeanDefinitionException}（实测 L1 一次 97 个用例全挂）。
     *    <p>而若把清单放进 {@code WebMvcConfigurer} 的 {@code @Bean}，
     *    又会与该配置的字段注入构成
     *    {@code BeanCurrentlyInCreationException: unresolvable circular reference}。
     *    <p><b>正解：可选依赖 + 缺失即不安装执行</b>。切片里没有清单本就无需鉴权；
     *    真实应用里清单是必有的（{@code load()} 失败会 fail-loud 拒绝启动）。
     *    <b>判据：被切片上下文间接依赖的协作对象，要么在切片白名单里，
     *    要么用可选依赖 —— 塞进 WebMvcConfigurer 的 @Bean 是第三种、通常最糟的选择。</b>
     */
    private final ObjectProvider<PermissionManifest> manifestProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PermissionEnforcementInterceptor(ObjectProvider<PermissionManifest> manifestProvider) {
        this.manifestProvider = manifestProvider;
    }

    /** 未命中端点的告警计数：按 (METHOD uri 前缀) 聚合，避免刷屏把日志淹掉。 */
    private final Map<String, AtomicLong> unregisteredHits = new ConcurrentHashMap<>();
    /** 「命中但缺权限」的告警计数（warn-only 下会被大量触发，必须去重）。 */
    private final Map<String, AtomicLong> missingPermissionHits = new ConcurrentHashMap<>();
    private static final int MAX_WARN_PER_ENDPOINT = 1;

    /** 清单缺失只告警一次，避免刷屏。 */
    private volatile boolean warnedMissingManifest = false;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof org.springframework.web.method.HandlerMethod)) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
            // 匿名请求交给 SecurityConfig 的白名单/认证处理，此处不重复判定
            return true;
        }

        PermissionManifest manifest = manifestProvider.getIfAvailable();
        if (manifest == null) {
            if (!warnedMissingManifest) {
                warnedMissingManifest = true;
                log.warn("未注入权限清单（PermissionManifest），本拦截器<b>不执行</b>。"
                        + "切片测试（@WebMvcTest）属预期情形；真实应用中清单缺失应当拒绝启动。");
            }
            return true;
        }

        String uri = request.getRequestURI();
        PermissionManifest.Entry entry = manifest.find(request.getMethod(), uri);

        if (entry == null) {
            warnUnregistered(request.getMethod(), uri);
            return true;
        }

        Set<String> authorities = auth.getAuthorities().stream()
                .map(a -> a.getAuthority()).collect(java.util.stream.Collectors.toSet());
        if (authorities.contains(entry.permission())) {
            return true;
        }

        String msg = "权限不足：该操作需要权限 " + entry.permission();
        if (!manifest.isEnforcing()) {
            // warn-only：登记 ≠ 拦截。此处若直接 403，等于**跳过批次 4 就 fail-closed**，
            // 与 D-1 裁定冲突（且会让「登记清单」这个纯文档动作变成破坏性变更）。
            warnMissingPermission(request.getMethod(), uri, entry.permission());
            return true;
        }
        log.warn("端点权限拒绝: {} {} 需要={}", request.getMethod(), uri, entry.permission());
        writeJson(response, R.forbidden(msg));
        return false;
    }

    /** 命中清单但缺权限的告警，同样按端点聚合去重。 */
    private void warnMissingPermission(String method, String uri, String permission) {
        String key = "缺权限 " + method + " " + shorten(uri);
        AtomicLong counter = missingPermissionHits.computeIfAbsent(key, k -> new AtomicLong());
        if (counter.incrementAndGet() <= MAX_WARN_PER_ENDPOINT) {
            log.warn("端点权限不足但当前为 warn-only 放行: {} 需要权限={}", key, permission);
        }
    }

    private void warnUnregistered(String method, String uri) {
        String key = method + " " + shorten(uri);
        AtomicLong counter = unregisteredHits.computeIfAbsent(key, k -> new AtomicLong());
        if (counter.incrementAndGet() <= MAX_WARN_PER_ENDPOINT) {
            log.warn("端点未在权限清单中登记（warn-only 放行）: {} —— 缺口由守卫 G2 棘轮盯住",
                    key);
        }
    }

    private static String shorten(String uri) {
        int cut = uri.lastIndexOf('/');
        return cut > 0 && uri.length() - cut <= 20 ? uri : uri.substring(0, Math.min(uri.length(), 60));
    }

    private void writeJson(HttpServletResponse response, R<?> body) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}