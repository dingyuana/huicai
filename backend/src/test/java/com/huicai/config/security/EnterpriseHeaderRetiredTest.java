package com.huicai.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * SPEC-P124 切片5：{@code X-Enterprise-Id} 请求头必须**已被废止**。
 *
 * <p><b>为什么需要这个守卫</b>：该头曾使「当前企业」存在三个来源，其中一个
 * <b>由客户端可控</b>（3 源 2 义 0 权威）。切片 4 拆分字段、切片 3 切换重签后，
 * 权威已收敛到 JWT，头通道必须同步关闭 —— 否则任何一侧漏改，客户端就又能
 * 绕过服务端重签去指定企业。
 *
 * <p><b>本守卫是结构性的</b>：读源码确认头读取与注入已消失。
 * 它<b>不</b>替代行为测试 —— 行为面由 {@code EnterpriseControllerTest} 的
 * 切换用例与端到端「不带头的请求仍能正确解析企业」承担。
 */
@DisplayName("SPEC-P124 X-Enterprise-Id 请求头已废止")
class EnterpriseHeaderRetiredTest {

    private static String read(String path) throws IOException {
        return Files.readString(Path.of("src/main/java/com/huicai", path));
    }

    @Test
    @DisplayName("过滤器不得再读取 X-Enterprise-Id（否则客户端仍可指定企业）")
    void filterMustNotReadHeader() throws IOException {
        String src = read("config/security/JwtAuthenticationFilter.java");
        assertFalse(src.contains("getHeader(\"X-Enterprise-Id\")"),
                "JwtAuthenticationFilter 仍在读取 X-Enterprise-Id —— "
                        + "客户端可绕过 /enterprise/switch 的校验与重签直接指定企业，"
                        + "「服务端为唯一权威」即不成立");
        assertFalse(src.contains("enterpriseMembershipChecker"),
                "过滤器不应再持有 EnterpriseMembershipChecker —— "
                        + "其唯一用途是请求头覆盖路径的成员校验，现已由切换端点承担");
    }

    @Test
    @DisplayName("SecurityConfig 不得再向过滤器注入已废止的校验/审计依赖")
    void securityConfigShouldNotInjectRetiredBeans() throws IOException {
        String src = read("config/security/SecurityConfig.java");
        assertFalse(src.contains("EnterpriseMembershipChecker"),
                "SecurityConfig 仍在注入 EnterpriseMembershipChecker");
        assertFalse(src.contains("EnterpriseSwitchAuditService"),
                "SecurityConfig 仍在注入 EnterpriseSwitchAuditService —— "
                        + "切换审计已改由 EnterpriseController#recordSwitchAudit 记录");
    }

    @Test
    @DisplayName("切换审计必须仍在切换发生处（废止请求头不得连带丢失审计）")
    void switchAuditMustStillExist() throws IOException {
        String src = read("agency/tenant/controller/EnterpriseController.java");
        org.junit.jupiter.api.Assertions.assertTrue(
                src.contains("recordEnterpriseSwitch"),
                "EnterpriseController 不再记录切换审计 —— "
                        + "该审计原写在请求头分支里，头被废止后若未搬运将整体丢失");
    }
}