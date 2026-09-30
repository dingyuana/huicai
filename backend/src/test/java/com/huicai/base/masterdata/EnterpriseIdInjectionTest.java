package com.huicai.base.masterdata;

import com.huicai.base.masterdata.entity.CustomerEntity;
import com.huicai.base.masterdata.mapper.CustomerMapper;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.entity.BaseEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * AT-102-7 —— 入参 {@code enterpriseId} 封禁（REQ-2026-129，P102 L1 兜底）
 *
 * <p>缺陷背景：{@code @RequestBody XxxEntity} 共 48 处 / 25 个 Controller。
 * {@code MyMetaObjectHandler.insertFill} 原本用 {@code strictInsertFill}，
 * 而该方法<b>仅当字段为 null 时才填</b>；{@code BaseEntity.enterpriseId} 又是
 * {@code FieldFill.INSERT} 的可写真实字段 ⇒ 请求体传 {@code {"enterpriseId": 999}}
 * 即可<b>绕过上下文直写他人租户</b>，且不依赖任何特制 header（比 header 越权更隐蔽）。
 *
 * <p>L1 修法：{@code insertFill} 对 {@code enterpriseId} 改为<b>无条件覆盖</b>为上下文值。
 *
 * <p>断言策略：正向「落库值 = 上下文」+ 负向「落库值 ≠ 入参伪造值」，
 * 两者缺一则无法排除「根本没写入」或「恰好相等」。
 */
@DisplayName("P102 入参 enterpriseId 封禁")
class EnterpriseIdInjectionTest extends AbstractMapperTest {

    private static final Long FORGED_ENTERPRISE_ID = 999L;

    @Autowired
    private CustomerMapper customerMapper;

    private CustomerEntity newCustomer(String code) {
        CustomerEntity c = new CustomerEntity();
        c.setCode(code);
        c.setName("注入测试客户-" + code);
        c.setIsActive(true);
        return c;
    }

    @Test
    @DisplayName("AT-102-7a 伪造 enterpriseId=999 必须被覆盖为上下文企业")
    void forgedEnterpriseIdIsOverridden() {
        CustomerEntity c = newCustomer("P102.INJ.A");
        c.setEnterpriseId(FORGED_ENTERPRISE_ID);

        customerMapper.insert(c);

        Long persisted = customerMapper.selectById(c.getId()).getEnterpriseId();
        assertNotNull(persisted, "落库 enterprise_id 不应为 null");
        assertEquals(DEFAULT_ENTERPRISE_ID, persisted,
                "伪造的 enterpriseId 未被覆盖，写入了他人租户");
    }

    @Test
    @DisplayName("AT-102-7b 负向：落库值绝不能等于伪造值")
    void persistedEnterpriseIdIsNeverTheForgedOne() {
        CustomerEntity c = newCustomer("P102.INJ.B");
        c.setEnterpriseId(FORGED_ENTERPRISE_ID);

        customerMapper.insert(c);

        Long persisted = customerMapper.selectById(c.getId()).getEnterpriseId();
        assertNotEqualsForged(persisted);
    }

    @Test
    @DisplayName("AT-102-7c 负向：切换上下文后应写入新上下文企业，而非入参值")
    void switchedContextWins() {
        useEnterprise(2L);
        CustomerEntity c = newCustomer("P102.INJ.C");
        c.setEnterpriseId(FORGED_ENTERPRISE_ID);

        customerMapper.insert(c);

        assertEquals(2L, customerMapper.selectById(c.getId()).getEnterpriseId(),
                "上下文已切到企业 2，落库却不是 2");
    }

    @Test
    @DisplayName("AT-102-7d 回归：未携带 enterpriseId 时仍按上下文回填（不得被本次改动破坏）")
    void absentEnterpriseIdStillFilled() {
        CustomerEntity c = newCustomer("P102.INJ.D");
        assertEquals(null, c.getEnterpriseId(), "用例前提：入参未设置 enterpriseId");

        customerMapper.insert(c);

        assertEquals(DEFAULT_ENTERPRISE_ID, customerMapper.selectById(c.getId()).getEnterpriseId(),
                "未携带 enterpriseId 时应照旧按上下文回填");
    }

    @Test
    @DisplayName("AT-102-7e 前提守卫：写入路径必须全部要求认证（保证生产必有企业上下文）")
    void allWriteEndpointsRequireAuthentication() {
        String src = readSecurityConfigSource();
        assertTrue(src.contains(".anyRequest().authenticated()"),
                "SecurityConfig 不再对全部请求要求认证 —— 一旦放开，"
                        + "无上下文的请求即可命中 insertFill 的 null 分支，L1 强制覆盖随即失效");
    }

    @Test
    @DisplayName("AT-102-7f 前提守卫：匿名白名单恰为登录/健康检查/文档，无任何业务写端点")
    void permitAllListIsExactlyTheSafeSet() {
        String src = readSecurityConfigSource();
        int start = src.indexOf(".permitAll()");
        assertTrue(start > 0, "未找到 permitAll 白名单");
        int open = src.lastIndexOf(".requestMatchers(", start);
        assertTrue(open > 0 && open < start, "未定位到 requestMatchers 块");

        String block = src.substring(src.indexOf('(', open) + 1, start);
        java.util.Set<String> actual = new java.util.LinkedHashSet<>();
        for (String raw : block.split(",")) {
            String v = raw.replaceAll("[\"\\s)]", "");
            if (!v.isEmpty()) {
                actual.add(v);
            }
        }

        java.util.Set<String> expected = new java.util.LinkedHashSet<>(java.util.List.of(
                "/api/v1/auth/login",
                "/api/v1/auth/test-password",
                "/api/v1/system/health",
                "/actuator/health",
                "/doc.html",
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/webjars/**",
                "/favicon.ico"));

        assertEquals(expected, actual,
                "匿名白名单被改动 —— 新增条目若含业务写端点，"
                        + "将出现无企业上下文的请求，L1 强制覆盖随之失效");
    }

    private String readSecurityConfigSource() {
        java.net.URL url = getClass().getClassLoader()
                .getResource("com/huicai/config/security/SecurityConfig.class");
        assertNotNull(url, "未找到 SecurityConfig.class");
        try {
            java.nio.file.Path p = java.nio.file.Paths.get(url.toURI());
            // class 文件不含源码可读文本，退化为对资源存在性的断言 + 反射检查注解
            return readAsText(p);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String readAsText(java.nio.file.Path bin) throws Exception {
        // SecurityConfig 位于 target/classes；源码在 src/main/java，两处拼出可读文本
        String pkg = "com/huicai/config/security/SecurityConfig.java";
        java.nio.file.Path src = java.nio.file.Paths.get("src/main/java", pkg);
        if (!java.nio.file.Files.exists(src)) {
            throw new IllegalStateException("找不到 SecurityConfig 源码: " + src.toAbsolutePath());
        }
        return java.nio.file.Files.readString(src);
    }

    private void assertNotEqualsForged(Long persisted) {
        if (FORGED_ENTERPRISE_ID.equals(persisted)) {
            throw new AssertionError("落库 enterprise_id 等于伪造值 " + FORGED_ENTERPRISE_ID
                    + "，入参封禁失效（跨租户写入）");
        }
    }

    @Test
    @DisplayName("AT-102-7g 反证：BaseEntity.enterpriseId 确实是可写真实字段（否则本类全部用例恒真）")
    void enterpriseIdIsARealWritableField() throws NoSuchFieldException {
        java.lang.reflect.Field f = BaseEntity.class.getDeclaredField("enterpriseId");
        assertEquals(Long.class, f.getType());
    }
}
