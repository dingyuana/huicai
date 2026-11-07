package com.huicai.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;

/**
 * 权限拒绝事件（SPEC-P154 项①：403 落审计）。
 *
 * <p>拦截器 {@code PermissionEnforcementInterceptor} 在 enforcing 模式下判定缺权限
 * 并返回 403 时发布。监听方 {@code AccessDeniedAuditListener} 落 {@code t_audit_log}，
 * module=SECURITY / operation=ACCESS_DENIED，after_data 携带 requiredPermission、uri、method、
 * IP、UA、stack(可选)。</p>
 *
 * <p>🔴 <b>不直接注入 AuditLogService</b>：拦截器会被 {@code @WebMvcTest} 切片加载，
 * 注入服务层 bean 会打挂切片测试（同 {@code ServerErrorEvent} 理由）。走事件总线，
 * 切片无监听器时静默通过。</p>
 */
@Getter
public class AccessDeniedEvent {

    private final String username;
    private final String requiredPermission;
    private final String httpMethod;
    private final String uri;
    private final String clientIp;
    private final String userAgent;

    public AccessDeniedEvent(String username, String requiredPermission, HttpServletRequest request) {
        this.username = username;
        this.requiredPermission = requiredPermission;
        if (request != null) {
            this.httpMethod = request.getMethod();
            this.uri = request.getRequestURI();
            // 审计面 IP 取 XFF 首跳（对齐 ClientLogController#clientIp / AuthController#getClientIp
            // 同一家法）：生产经 nginx/vite 代理，getRemoteAddr 恒为代理地址，不还原等于审计无 IP
            this.clientIp = clientIp(request);
            this.userAgent = request.getHeader("User-Agent");
        } else {
            this.httpMethod = null;
            this.uri = null;
            this.clientIp = null;
            this.userAgent = null;
        }
    }

    /** X-Forwarded-For 首跳优先（与 P152/P197 上报端点同口径）。 */
    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty() && !"unknown".equalsIgnoreCase(xff)) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}