package com.huicai.common.exception;

import com.huicai.base.system.entity.AuditLogEntity;
import com.huicai.base.system.service.AuditLogService;
import com.huicai.common.util.JsonSafe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 权限拒绝落库监听（SPEC-P154 项①：403 落审计）。
 *
 * <p>监听 {@code AccessDeniedEvent}，写 {@code t_audit_log}，module=SECURITY
 * operation=ACCESS_DENIED，status=fail。after_data 携带 requiredPermission、
 * uri、method、ip、ua、timestamp。</p>
 *
 * <p>🔴 <b>AFTER_COMPLETION + fallbackExecution=true</b>：被拒请求可能已标记
 * rollback-only（如方法级 @Transactional 但上层已 rollback-only）。AFTER_COMPLETION
 * 保证业务事务终结后再写；fallbackExecution 覆盖「端点本无事务」情形。</p>
 *
 * <p>审计写入失败只 warn —— 留痕是旁路，绝不反噬业务。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessDeniedAuditListener {

    private final AuditLogService auditLogService;

    static final int MAX_STACK_LEN = 5000;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION, fallbackExecution = true)
    public void onAccessDenied(AccessDeniedEvent event) {
        try {
            AuditLogEntity record = new AuditLogEntity();
            record.setModule("SECURITY");
            record.setOperation("ACCESS_DENIED");
            record.setStatus("fail");
            record.setUsername(event.getUsername());
            record.setIpAddress(event.getClientIp());
            record.setUserAgent(JsonSafe.truncate(event.getUserAgent(), 500));
            if (event.getHttpMethod() != null && event.getUri() != null) {
                record.setRequestParams(event.getHttpMethod() + " " + event.getUri());
            }
            record.setAfterData("{\"requiredPermission\":\"" + JsonSafe.escape(event.getRequiredPermission())
                    + "\",\"method\":\"" + JsonSafe.escape(event.getHttpMethod())
                    + "\",\"uri\":\"" + JsonSafe.escape(event.getUri())
                    + "\",\"ip\":\"" + JsonSafe.escape(event.getClientIp())
                    + "\",\"userAgent\":\"" + JsonSafe.escape(event.getUserAgent()) + "\"}");
            auditLogService.save(record);
        } catch (Exception saveEx) {
            log.warn("权限拒绝落库失败（已在拦截器日志中留痕）: {}", saveEx.getMessage());
        }
    }
}