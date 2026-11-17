package com.huicai.base.system.controller;

import com.huicai.base.system.entity.AuditLogEntity;
import com.huicai.base.system.service.AuditLogService;
import com.huicai.common.response.R;
import com.huicai.common.util.JsonSafe;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 前端错误上报入口（SPEC-P152）。
 *
 * <p>浏览器端的 Vue 渲染错误 / 未处理 Promise rejection / 5xx 响应由
 * {@code frontend/src/utils/errorReporter.ts} 送来这里，落
 * {@code t_audit_log}（module=FRONTEND），「操作日志」页按模块可查。</p>
 *
 * <p><b>为什么 permitAll</b>：登录页/登录前的报错同样要能捕捉（例如企业信息加载失败），
 * 这类报错恰恰历史上完全不可见。放开公网写口的对冲：</p>
 * <ul>
 *   <li><b>长度封顶</b>：message≤500 / stack≤8000 / url≤1000，超出截断，杜绝大包灌库；</li>
 *   <li><b>IP 限流</b>：每分钟 60 条，超出静默丢弃（返回仍为 200，不给探测者反馈）；</li>
 *   <li><b>不信任输入</b>：字段只进审计的展示列，绝不参与任何 SQL 拼接。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/system/audit-log")
@RequiredArgsConstructor
public class ClientLogController {

    private final AuditLogService auditLogService;

    static final int MAX_MESSAGE_LEN = 500;
    static final int MAX_STACK_LEN = 8000;
    static final int MAX_URL_LEN = 1000;
    static final int RATE_LIMIT_PER_MIN = 60;
    static final long RATE_WINDOW_MS = 60_000L;

    /** 单进程内存限流：ip -> [窗口起点(整分), 计数]。多实例各自独立，单实例部署够用。 */
    private final ConcurrentHashMap<String, long[]> rateMap = new ConcurrentHashMap<>();

    @Data
    public static class ClientLogRequest {
        /** ERROR / WARN（其余一律按 ERROR 记） */
        private String level;
        private String message;
        private String stack;
        /** 出错页面 URL（location.href） */
        private String url;
    }

    @Operation(summary = "前端错误上报（登录态可选；限流+截断）")
    @PostMapping("/client")
    public R<Void> report(@RequestBody(required = false) ClientLogRequest req, HttpServletRequest request) {
        if (req == null || req.getMessage() == null || req.getMessage().isBlank()) {
            return R.ok(null); // 空报文静默成功，不给探测信号
        }
        String ip = clientIp(request);
        if (!tryAcquire(ip)) {
            log.debug("前端错误上报限流丢弃: ip={}", ip);
            return R.ok(null);
        }

        boolean warn = "WARN".equalsIgnoreCase(req.getLevel());
        AuditLogEntity entity = new AuditLogEntity();
        entity.setModule("FRONTEND");
        entity.setOperation(warn ? "JS_WARN" : "JS_ERROR");
        entity.setStatus(warn ? "warn" : "fail");
        entity.setUsername(currentUsername());
        entity.setIpAddress(ip);
        entity.setUserAgent(JsonSafe.truncate(request.getHeader("User-Agent"), 500));
        entity.setRequestParams(JsonSafe.truncate(req.getUrl(), MAX_URL_LEN));
        entity.setResponseResult(JsonSafe.truncate(req.getMessage(), MAX_MESSAGE_LEN));
        entity.setAfterData(buildStackJson(req, warn));

        auditLogService.save(entity);
        return R.ok(null);
    }

    /** 堆栈进 after_data（合法 JSON）；无堆栈时只记 message，省存储 */
    String buildStackJson(ClientLogRequest req, boolean warn) {
        String message = JsonSafe.escape(JsonSafe.truncate(req.getMessage(), MAX_MESSAGE_LEN));
        String stack = JsonSafe.truncate(req.getStack(), MAX_STACK_LEN);
        if (stack == null || stack.isBlank()) {
            return "{\"message\":\"" + message + "\"}";
        }
        return "{\"message\":\"" + message
                + "\",\"stack\":\"" + JsonSafe.escape(stack)
                + "\",\"level\":\"" + (warn ? "WARN" : "ERROR") + "\"}";
    }

    /** 按分钟粗粒度限流（窗口起点整分对齐） */
    boolean tryAcquire(String ip) {
        long window = System.currentTimeMillis() / RATE_WINDOW_MS;
        long[] slot = rateMap.compute(ip, (k, v) -> {
            if (v == null || v[0] != window) return new long[]{window, 1};
            v[1]++;
            return v;
        });
        // 防 key 泄漏：map 过大时清掉非当前窗口的陈旧项
        if (rateMap.size() > 10_000) {
            rateMap.entrySet().removeIf(entry -> entry.getValue()[0] != window);
        }
        return slot[1] <= RATE_LIMIT_PER_MIN;
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty() && !"unknown".equalsIgnoreCase(xff)) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String currentUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                return auth.getName();
            }
        } catch (Exception ignore) {
            // 登录前上报无上下文
        }
        return "anonymous";
    }
}
