package com.huicai.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class JwtProvider {

    private final SecretKey key;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    @PostConstruct
    public void validateConfig() {
        if (key.getEncoded().length < 32) {
            log.error("JWT secret 长度不足 32 字节（当前 {} 字节），HMAC-SHA256 要求至少 32 字节。请设置环境变量 JWT_SECRET。",
                    key.getEncoded().length);
            throw new IllegalStateException("JWT secret 长度不足，请设置环境变量 JWT_SECRET（至少 32 字符）");
        }
        log.info("JWT 配置校验通过，secret 长度 {} 字节", key.getEncoded().length);
    }

    public String generateAccessToken(String username, Long userId, List<String> roles,
                                       Long enterpriseId, Long agencyId, String userType, String agencyRole) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                // SPEC-P124（P0）：类型标记。缺它 refresh token 可被当 access token 使用。
                .claim("type", "access")
                .claim("userId", userId)
                .claim("roles", roles)
                .claim("enterpriseId", enterpriseId)
                .claim("agencyId", agencyId)
                .claim("userType", userType)
                .claim("agencyRole", agencyRole)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiration))
                .signWith(key)
                .compact();
    }

    /**
     * 签发 refresh token。
     *
     * <p><b>SPEC-P124 为何必须带上完整 claim</b>：refresh 的用途是「凭长期凭证换新的
     * access token」。若它只带 {@code subject}，换证时就无法还原<b>当前企业上下文</b> ——
     * 代理会计切到客户 2 后一旦 access token 过期，续期拿回的却是企业为 null 的 token，
     * 用户会被无声地弹回客户列表。故此处与 access token 携带同一组 claim。
     */
    public String generateRefreshToken(String username, Long userId, List<String> roles,
                                       Long enterpriseId, Long agencyId,
                                       String userType, String agencyRole) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("type", "refresh")
                .claim("userId", userId)
                .claim("roles", roles)
                .claim("enterpriseId", enterpriseId)
                .claim("agencyId", agencyId)
                .claim("userType", userType)
                .claim("agencyRole", agencyRole)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpiration))
                .signWith(key)
                .compact();
    }

    /**
     * 校验 refresh token。
     *
     * <p>与 {@link #validateToken} 分开：两者分别把关 access / refresh 两种用途，
     * 混用会导致任一方向都可冒用（SPEC-P124 P0 已修过一次）。
     */
    public boolean validateRefreshToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return "refresh".equals(claims.get("type", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 校验 token 是否为<b>可用的 access token</b>。
     *
     * <p><b>SPEC-P124（P0）token 类型混淆</b>：本方法此前只校验签名与有效期，
     * 而 {@code generateRefreshToken} 产出的 refresh token 有效期为 <b>7 天</b>，
     * 且同样带 {@code subject}。实测拿 refresh token 直接调业务接口可返回 200 与真实数据
     * ⇒ 把 access token 有效期从 24h 缩到 30min 将<b>形同虚设</b>
     * （持 refresh token 者可无限续用）。
     *
     * <p>因此这里除签名外<b>必须</b>校验 {@code type == "access"}。
     *
     * <p>⚠️ <b>行为变更</b>：修复前签发、且不带 {@code type} 的 token 将一律失效，
     * 所有人需重新登录。这是安全修复的预期代价（开发库可接受）；
     * 若将来需要无感过渡，需引入 token 版本号而非放宽本校验。
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return "access".equals(claims.get("type", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        return getClaims(token).getSubject();
    }

    public Long getUserIdFromToken(String token) {
        return getClaims(token).get("userId", Long.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        return getClaims(token).get("roles", List.class);
    }

    public Long getEnterpriseIdFromToken(String token) {
        Object val = getClaims(token).get("enterpriseId");
        if (val instanceof Integer) return ((Integer) val).longValue();
        return (Long) val;
    }

    public Long getAgencyIdFromToken(String token) {
        Object val = getClaims(token).get("agencyId");
        if (val == null) return null;
        if (val instanceof Integer) return ((Integer) val).longValue();
        return (Long) val;
    }

    public String getUserTypeFromToken(String token) {
        return getClaims(token).get("userType", String.class);
    }

    public String getAgencyRoleFromToken(String token) {
        return getClaims(token).get("agencyRole", String.class);
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
