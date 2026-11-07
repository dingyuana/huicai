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
 * 兜底未知异常的落库监听（SPEC-P152，{@link GlobalExceptionHandler#handleUnknown} 的另一半）。
 *
 * <p>🔴 <b>AFTER_COMPLETION + fallbackExecution=true</b>：走到兜底的事务型请求大概率已被
 * 标记 rollback-only。若在<b>事务中</b>直接 {@code save}（REQUIRED）会加入脏事务 ⇒
 * 审计行随业务回滚蒸发，或提交时 {@code UnexpectedRollbackException} 把 500 变二次故障
 * （P137 同款机制）。AFTER_COMPLETION 保证<b>业务事务已终结</b>后才写；
 * {@code fallbackExecution=true} 覆盖「端点本就没有事务」的情形（否则事件被静默丢弃）。</p>
 *
 * <p>审计写入失败只 warn —— 留痕是旁路，绝不反噬业务。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServerErrorAuditListener {

    private final AuditLogService auditLogService;

    /** after_data 里堆栈的截断上限（字符）—— 留排障线索，不塞爆审计表 */
    static final int MAX_STACK_LEN = 8000;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION, fallbackExecution = true)
    public void onServerError(ServerErrorEvent event) {
        try {
            AuditLogEntity record = new AuditLogEntity();
            record.setModule("SYSTEM");
            record.setOperation("SERVER_ERROR");
            record.setStatus("fail");
            record.setUsername(event.getUsername());
            record.setIpAddress(event.getClientIp());
            record.setUserAgent(JsonSafe.truncate(event.getUserAgent(), 500));
            if (event.getHttpMethod() != null) {
                record.setRequestParams(event.getHttpMethod() + " " + event.getUri());
            }
            Throwable e = event.getException();
            record.setAfterData("{\"exception\":\"" + JsonSafe.escape(e.getClass().getName())
                    + "\",\"message\":\"" + JsonSafe.escape(e.getMessage())
                    + "\",\"stack\":\"" + JsonSafe.escape(stackTraceOf(e)) + "\"}");
            auditLogService.save(record);
        } catch (Exception saveEx) {
            log.warn("系统异常落库失败（堆栈已在 handleUnknown 的 error 日志中）: {}", saveEx.getMessage());
        }
    }

    static String stackTraceOf(Throwable e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        String s = sw.toString();
        return s.length() > MAX_STACK_LEN ? s.substring(0, MAX_STACK_LEN) + "...(truncated)" : s;
    }
}
