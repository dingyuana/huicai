package com.huicai.security;

import com.huicai.common.config.PermissionWebMvcConfig;
import com.huicai.common.security.PermissionEnforcementInterceptor;
import com.huicai.common.security.PermissionManifest;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P133 批次 2 —— {@code PermissionEnforcementInterceptor} 的守卫。
 *
 * <p>🔴 <b>为什么要拆成「逻辑」与「注册」两条</b>：只测 {@code preHandle} 只能证明
 * 逻辑正确，证明不了<b>它真的被接进请求链</b>；只测注册则证明不了逻辑。
 * 两者都测，才等价于「拦截器生效」——
 * 判据同 §4.5 第 21 条：<b>门禁自身的执行也必须被验证</b>。</p>
 *
 * <p>⚠️ 真值源是「清单」与「Spring 上下文」，<b>不扫源码</b>（P134 §8.7 的教训）。</p>
 */
class PermissionEnforcementInterceptorRealDBTest extends AbstractMapperTest {

    @Autowired
    private PermissionManifest manifest;

    @Autowired
    private PermissionEnforcementInterceptor interceptor;

    @Autowired
    private PermissionWebMvcConfig webMvcConfig;

    private static final HandlerMethod SOME_HANDLER = handlerMethod();

    private static HandlerMethod handlerMethod() {
        try {
            Method m = Object.class.getMethod("toString");
            return new HandlerMethod(new Object(), m);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private void loginAs(String user, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user, "n/a", AuthorityUtils.createAuthorityList(authorities)));
    }

    private MockHttpServletResponse invoke(String method, String uri) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRequestURI(uri);
        MockHttpServletResponse res = new MockHttpServletResponse();
        interceptor.preHandle(req, res, SOME_HANDLER);
        return res;
    }

    // ───────── 逻辑 ─────────

    @Test
    @DisplayName("清单已加载：批次 1 登记的 19 条（全是写端点）")
    void manifestLoads() {
        assertNotNull(manifest);
        assertEquals(19, manifest.size(), "批次 1 清单条数应为 19（变更时同步改本断言）");
        long writes = manifest.entries().stream().filter(PermissionManifest.Entry::write).count();
        assertEquals(19, writes, "这 19 条全是写端点（清库 9 + 高危删除 8 + 结账 2）");
    }

    @Test
    @DisplayName("路径匹配：{id} 单段匹配且不跨段（安全边界）")
    void pathVariableMatchesSingleSegmentOnly() {
        var pat = PermissionManifest.toPattern("/api/v1/system/role/{id}");
        assertTrue(pat.matcher("/api/v1/system/role/4").matches(), "单段应命中");
        assertFalse(pat.matcher("/api/v1/system/role/4/extra").matches(),
                "跨段不应命中 —— 否则 /role/{id}/x 会被 /role/{id} 的权限错误覆盖");
        assertFalse(pat.matcher("/api/v1/system/role/").matches(), "空段不应命中");
    }

    @Test
    @DisplayName("命中清单但缺权限 ⇒ 403 且响应体含所需权限码")
    void registeredEndpointWithoutAuthorityGets403() throws Exception {
        loginAs("nobody");
        MockHttpServletResponse res = invoke("DELETE", "/api/v1/system/role/1");
        assertEquals(403, res.getStatus(), "缺权限必须 403");
        assertTrue(res.getContentAsString().contains("system:role:list"),
                "403 响应须指名所需权限码，实际：" + res.getContentAsString());
    }

    @Test
    @DisplayName("命中清单且持有权限 ⇒ 放行（返回 true，未写响应）")
    void registeredEndpointWithAuthorityPasses() throws Exception {
        loginAs("admin", "system:role:list");
        MockHttpServletResponse res = invoke("DELETE", "/api/v1/system/role/1");
        assertTrue(res.getContentAsString().isEmpty(),
                "放行时不应写 403 响应体，实际：" + res.getContentAsString());
    }

    @Test
    @DisplayName("未登记端点 ⇒ warn-only 放行（D-1 裁定），不得 403")
    void unregisteredEndpointIsWarnOnly() throws Exception {
        loginAs("nobody");
        MockHttpServletResponse res = invoke("GET", "/api/v1/definitely/not/registered");
        assertTrue(res.getContentAsString().isEmpty(),
                "批次 2 是 warn-only，未登记端点必须放行。若这里被拒，"
                        + "说明有人提前把 fail-closed 打开了。实际：" + res.getContentAsString());
    }

    @Test
    @DisplayName("匿名请求直接跳过（认证交给 SecurityConfig，不重复判定）")
    void anonymousRequestIsSkipped() throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletResponse res = invoke("DELETE", "/api/v1/system/role/1");
        assertTrue(res.getContentAsString().isEmpty(),
                "匿名请求不应被本拦截器拒绝（否则会破坏 9 条 permitAll 白名单）");
    }

    @Test
    @DisplayName("非 HandlerMethod（如静态资源）直接放行")
    void nonHandlerMethodIsSkipped() throws Exception {
        loginAs("nobody");
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("DELETE", "/api/v1/x"),
                new MockHttpServletResponse(), new Object()),
                "非 HandlerMethod 不应拦截");
    }

    @Test
    @DisplayName("find() 对未登记路径必须返回 null（warn 分支可达）")
    void findReturnsNullForUnregistered() {
        assertNull(manifest.find("GET", "/api/v1/definitely/not/registered"),
                "未登记路径必须返回 null，否则拦截器会误判为已登记");
        assertNotNull(manifest.find("DELETE", "/api/v1/system/role/1"),
                "已登记路径必须能命中");
    }

    // ───────── 注册（证明它真被接进请求链） ─────────

    @Test
    @DisplayName("拦截器确实被注册进 MVC 拦截器链（证明它真被接进请求链）")
    void interceptorIsActuallyRegistered() throws Exception {

        InterceptorRegistry registry = new InterceptorRegistry();
        webMvcConfig.addInterceptors(registry);

        // ⚠️ InterceptorRegistry#getInterceptors() 是 protected，
        //    而 MockMvcBuilder#addInterceptors 在本项目所用 Spring 版本里不可用
        //    ⇒ 只能反射读私有 registrations 字段。
        //    判据：这是**结构性事实**（注册链里有没有它），不涉及业务判断，
        //    反射的成本可接受；用反射前先确认没有公开 API，否则优先用公开 API。
        var field = InterceptorRegistry.class.getDeclaredField("registrations");
        field.setAccessible(true);
        var registrations = (java.util.List<?>) field.get(registry);

        assertFalse(registrations.isEmpty(), "拦截器未注册 —— 那等于没接进请求链");

        // ⚠️ 列表里装的是 InterceptorRegistration **包装器**而非拦截器本身 ——
        //    首版直接 `instanceof` 断言必然失败。
        //    🔴 这是**守卫自身的错断言**：它让我一度以为「注册没生效」，
        //    差点去改生产代码。判据：守卫报错时先问「是代码错还是断言错」
        //    （与 §4.5 第 44 条「断言转红时先问是代码错了还是世界变了」同源）。
        // ⚠️ InterceptorRegistration#getInterceptor() 也是 protected，
        //    故连同包装器内部字段一起反射读取。
        var interceptorField = org.springframework.web.servlet.config.annotation
                .InterceptorRegistration.class.getDeclaredField("interceptor");
        interceptorField.setAccessible(true);
        boolean found = registrations.stream().anyMatch(r -> {
            try {
                return interceptorField.get(r) instanceof PermissionEnforcementInterceptor;
            } catch (IllegalAccessException e) {
                return false;
            }
        });
        assertTrue(found,
                "注册链里必须含 PermissionEnforcementInterceptor，实际：" + registrations);
    }
}