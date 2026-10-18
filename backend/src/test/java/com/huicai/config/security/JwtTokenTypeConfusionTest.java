package com.huicai.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P124（P0）：token 类型混淆守卫。
 *
 * <p><b>缺陷</b>：{@code JwtProvider#validateToken} 此前只校验签名与有效期。
 * {@code generateRefreshToken} 产出的 refresh token 有效期 <b>7 天</b>且同样带
 * {@code subject}，实测<b>直接当 access token 调业务接口可返回 200 与真实数据</b>。
 *
 * <p><b>为什么是 P0</b>：它使「缩短 access token 有效期」这一安全措施<b>完全失效</b> ——
 * 持有 refresh token 者可无限期续用。修复前任何有效期调整都是自欺。
 *
 * <p>本守卫直接对 {@link JwtProvider} 断言，<b>不经过滤器</b>：因为过滤器的行为
 * 还受 SecurityContext 影响，而缺陷发生在<b>校验函数本身</b>。
 */
@DisplayName("SPEC-P124 refresh token 不得充当 access token")
class JwtTokenTypeConfusionTest {

    /** 32 字节密钥，满足 HS256 要求 */
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private JwtProvider provider() {
        return new JwtProvider(SECRET, 30 * 60 * 1000L, 7 * 24 * 60 * 60 * 1000L);
    }

    @Test
    @DisplayName("refresh token 必须被 validateToken 判为不可用")
    void refreshTokenMustNotAuthenticate() {
        String refresh = newRefreshToken();
        assertFalse(provider().validateToken(refresh),
                "refresh token 被当作 access token 接受 ⇒ token 类型混淆成立，"
                        + "access token 有效期形同虚设");
    }

    /** SPEC-P124：refresh 端点的前置，refresh 自己也必须能被独立校验。 */
    @Test
    @DisplayName("refresh token 必须被 validateRefreshToken 接受（否则 /refresh 无法兑换）")
    void refreshTokenIsAcceptedByItsOwnValidator() {
        assertTrue(provider().validateRefreshToken(newRefreshToken()));
    }

    /** 反向：access token 不得被 refresh 校验器接受，否则等于没修类型混淆。 */
    @Test
    @DisplayName("access token 必须被 validateRefreshToken 判为不可用")
    void accessTokenMustNotAuthenticateAsRefresh() {
        String access = provider().generateAccessToken(
                "someuser", 1L, List.of("ADMIN"), 7L, null, "SUPER_ADMIN", null);
        assertFalse(provider().validateRefreshToken(access),
                "access token 被当 refresh 接受 ⇒ 凭 30 分钟的 access token 即可无限续期");
    }

    private String newRefreshToken() {
        return provider().generateRefreshToken(
                "someuser", 1L, List.of("ACCOUNTANT"), 2L, 1L, "AGENCY", "ACCOUNTANT");
    }

    @Test
    @DisplayName("正常 access token 必须仍然可用（反证：别把正路也堵死）")
    void accessTokenStillValid() {
        String access = provider().generateAccessToken(
                "someuser", 1L, List.of("ADMIN"), 7L, null, "SUPER_ADMIN", null);
        assertTrue(provider().validateToken(access),
                "正常 access token 被拒 ⇒ 修复过头，所有人无法登录");
    }

    @Test
    @DisplayName("换证后的 access token（企业号已变）同样可用 —— P124 切片 3 的前提")
    void reissuedAccessTokenIsValid() {
        JwtProvider p = provider();
        String reissued = p.generateAccessToken(
                "someuser", 1L, List.of("ACCOUNTANT"), 2L, 1L, "AGENCY", "ACCOUNTANT");
        assertTrue(p.validateToken(reissued));
        assertTrue(p.getEnterpriseIdFromToken(reissued) == 2L,
                "换证必须能把新的企业号写进 token，否则「切换即换证」无法生效");
    }
}