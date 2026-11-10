package com.huicai.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;

/**
 * 服务端未知异常事件（SPEC-P152）。
 *
 * <p><b>为什么用事件而不是直接注入 AuditLogService</b>：{@code @RestControllerAdvice}
 * 会被 {@code @WebMvcTest} 切片自动装配，若 handler 依赖服务层 bean，所有 WebMvc 切片
 * 上下文都会因缺 bean 起不来（实测打挂 97 个契约测试）。
 * {@code ApplicationEventPublisher} 是 Spring 核心设施，任何上下文（含切片）都有；
 * 切片里没有监听器时发布即无操作 —— 天然兼容。</p>
 *
 * <p>监听方：{@code ServerErrorAuditListener}（落 t_audit_log，module=SYSTEM）。</p>
 */
@Getter
public class ServerErrorEvent {

    private final Throwable exception;
    private final String username;
    private final String httpMethod;
    private final String uri;
    private final String clientIp;
    private final String userAgent;

    public ServerErrorEvent(Throwable exception, String username, HttpServletRequest request) {
        this.exception = exception;
        this.username = username;
        if (request != null) {
            this.httpMethod = request.getMethod();
            this.uri = request.getRequestURI();
            this.clientIp = request.getRemoteAddr();
            this.userAgent = request.getHeader("User-Agent");
        } else {
            this.httpMethod = null;
            this.uri = null;
            this.clientIp = null;
            this.userAgent = null;
        }
    }
}
