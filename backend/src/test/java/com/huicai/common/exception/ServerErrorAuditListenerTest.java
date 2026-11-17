package com.huicai.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.base.system.entity.AuditLogEntity;
import com.huicai.base.system.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SPEC-P152：兜底异常审计落库监听器。
 *
 * <p>守卫三件事：① 500 必须同时产生一条可查询的审计记录（否则容器重启堆栈即丢）；
 * ② after_data 必须是<b>合法 JSON</b>（JSONB 列，手写转义错了整列报废 ——
 * AGENTS §4.5 第 66 条同款风险）；③ 落库失败绝不能把 500 变成二次故障。</p>
 */
@ExtendWith(MockitoExtension.class)
class ServerErrorAuditListenerTest {

    @Mock private AuditLogService auditLogService;
    @InjectMocks private ServerErrorAuditListener listener;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ServerErrorEvent event(Throwable e) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/report/balance");
        request.setRemoteAddr("10.1.2.3");
        request.addHeader("User-Agent", "junit-agent");
        return new ServerErrorEvent(e, "tester", request);
    }

    @Test
    void 落库SYSTEM_SERVER_ERROR_堆栈进after_data() {
        listener.onServerError(event(new IllegalStateException("余额计算炸了")));

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogService).save(captor.capture());
        AuditLogEntity saved = captor.getValue();
        assertEquals("SYSTEM", saved.getModule());
        assertEquals("SERVER_ERROR", saved.getOperation());
        assertEquals("fail", saved.getStatus());
        assertEquals("tester", saved.getUsername());
        assertEquals("10.1.2.3", saved.getIpAddress());
        assertEquals("GET /api/v1/report/balance", saved.getRequestParams());
        assertTrue(saved.getAfterData().contains("IllegalStateException"));
        assertTrue(saved.getAfterData().contains("余额计算炸了"));
    }

    @Test
    void after_data必须是合法JSON_含引号换行反斜杠的堆栈也不例外() throws Exception {
        // 堆栈里塞引号/换行/反斜杠 —— 转义漏一项，readTree 就炸（JSONB 写入必失败）
        listener.onServerError(event(new RuntimeException("他说\"不行\"\nC:\\path\\to.java")));

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogService).save(captor.capture());
        var node = objectMapper.readTree(captor.getValue().getAfterData());
        assertTrue(node.get("stack").asText().contains("java.lang.RuntimeException"));
        assertEquals("他说\"不行\"\nC:\\path\\to.java", node.get("message").asText());
    }

    @Test
    void 堆栈超长_截断留标记_不撑爆JSONB() {
        listener.onServerError(event(new RuntimeException("x".repeat(ServerErrorAuditListener.MAX_STACK_LEN * 2))));

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogService).save(captor.capture());
        var node = assertDoesNotThrow(() -> objectMapper.readTree(captor.getValue().getAfterData()));
        assertTrue(node.get("stack").asText().endsWith("...(truncated)"));
    }

    @Test
    void 落库失败_吞掉异常_绝不反噬请求() {
        doThrow(new RuntimeException("审计表炸了")).when(auditLogService).save(any());
        assertDoesNotThrow(() -> listener.onServerError(event(new RuntimeException("x"))));
    }
}
