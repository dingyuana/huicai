package com.huicai.base.system.controller;

import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AT-102-5 / AT-102-3 —— 清库端点鉴权（REQ-2026-129，P102）
 *
 * <p>缺陷背景：{@link SystemClearController} 的 8 个端点全部无鉴权，
 * 任何登录用户（甚至未登录）都能 {@code DELETE FROM t_*} 清空全库财务数据。
 * {@code @EnableMethodSecurity} 已在 {@code SecurityConfig:24} 开启，
 * 故只要补上 {@code @PreAuthorize} 即真实生效。
 *
 * <p>本类为**反射 + 真实 DB** 双重断言：既要求注解存在（正向），
 * 又要求其权限码在 {@code t_menu.permission} 中真实存在（防「写了注解但权限码是假的」
 * 导致所有用户一律 403 的假修复）。
 */
@DisplayName("P102 端点鉴权")
class SystemClearAuthorizationTest extends AbstractMapperTest {

    private static final Pattern AUTHORITY = Pattern.compile("hasAuthority\\('([^']+)'\\)");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private List<Method> clearEndpoints() {
        List<Method> methods = new ArrayList<>();
        for (Method m : SystemClearController.class.getDeclaredMethods()) {
            if (m.isAnnotationPresent(PostMapping.class)) {
                methods.add(m);
            }
        }
        return methods;
    }

    @Test
    @DisplayName("AT-102-5a 9 个破坏性清库端点数量符合预期（防新增端点漏保护）")
    void clearEndpointsCountIsNine() {
        assertEquals(9, clearEndpoints().size(),
                "SystemClearController 清库端点数量变化，需同步核对是否全部受保护");
    }

    @Test
    @DisplayName("AT-102-3a 每个清库端点都必须有 @PreAuthorize 保护")
    void everyClearEndpointIsProtected() {
        List<String> unprotected = new ArrayList<>();
        for (Method m : clearEndpoints()) {
            if (!m.isAnnotationPresent(PreAuthorize.class)) {
                unprotected.add(m.getName());
            }
        }
        assertTrue(unprotected.isEmpty(),
                "以下清库端点缺少 @PreAuthorize，任何登录用户都可清空全库：" + unprotected);
    }

    @Test
    @DisplayName("AT-102-5b 反结账端点必须有 @PreAuthorize 保护")
    void periodReopenIsProtected() throws NoSuchMethodException {
        Method reopen = com.huicai.base.voucher.controller.PeriodCloseController.class
                .getMethod("reopen", String.class);
        assertTrue(reopen.isAnnotationPresent(PreAuthorize.class),
                "反结账端点缺少 @PreAuthorize");
    }

    @Test
    @DisplayName("AT-102-5c 注解引用的权限码必须在 t_menu.permission 中真实存在")
    void allPermissionCodesExistInMenuTable() {
        Set<String> declared = new HashSet<>();
        for (Method m : clearEndpoints()) {
            collectAuthorities(m, declared);
        }
        try {
            Method reopen = com.huicai.base.voucher.controller.PeriodCloseController.class
                    .getMethod("reopen", String.class);
            collectAuthorities(reopen, declared);
        } catch (NoSuchMethodException ignored) {
        }

        assertFalse(declared.isEmpty(), "未解析到任何权限码，测试本身失效");

        List<String> missing = new ArrayList<>();
        for (String code : declared) {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM t_menu WHERE permission = ? AND deleted = 0", Integer.class, code);
            if (cnt == null || cnt == 0) {
                missing.add(code);
            }
        }
        assertTrue(missing.isEmpty(),
                "以下权限码在 t_menu.permission 中不存在，将导致所有用户一律 403：" + missing);
    }

    @Test
    @DisplayName("AT-102-5d 负向：库里不存在的权限码必须被查出（防断言恒真）")
    void nonExistentPermissionCodeIsDetected() {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_menu WHERE permission = ? AND deleted = 0",
                Integer.class, "no:such:permission:code");
        assertEquals(0, cnt, "不存在的权限码应查出 0 条，测试断言链本身有效");
    }

    private void collectAuthorities(Method m, Set<String> out) {
        PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
        if (pa == null) {
            return;
        }
        Matcher matcher = AUTHORITY.matcher(pa.value());
        while (matcher.find()) {
            out.add(matcher.group(1));
        }
    }
}
