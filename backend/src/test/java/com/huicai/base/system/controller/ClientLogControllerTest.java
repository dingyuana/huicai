package com.huicai.base.system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.base.system.entity.AuditLogEntity;
import com.huicai.base.system.service.AuditLogService;
import com.huicai.common.response.R;
import org.junit.jupiter.api.BeforeEach;
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
 * SPEC-P152：前端错误上报端点行为。
 *
 * <p>该端点是 permitAll 的公网写口，守卫的重点是<b>防护面</b>：截断、限流、
 * 空报文静默、after_data 合法 JSON（JSONB 列，见 §4.5 第 66 条）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ClientLogControllerTest {

    @Mock private AuditLogService auditLogService;
    @InjectMocks private ClientLogController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockHttpServletRequest request(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/system/audit-log/client");
        req.setRemoteAddr(ip);
        req.addHeader("User-Agent", "Mozilla/5.0 junit");
        return req;
    }

    private ClientLogController.ClientLogRequest req(String level, String message, String stack) {
        ClientLogController.ClientLogRequest r = new ClientLogController.ClientLogRequest();
        r.setLevel(level);
        r.setMessage(message);
        r.setStack(stack);
        r.setUrl("http://app/#/finance/voucher");
        return r;
    }

    @Test
    void ERROR上报_JS_ERROR落库_堆栈JSON合法() throws Exception {
        R<Void> r = controller.report(req("ERROR", "Cannot read properties of undefined", "TypeError: x\n  at a.ts:1"), request("10.0.0.1"));

        assertEquals(200, r.getCode());
        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogService).save(captor.capture());
        AuditLogEntity saved = captor.getValue();
        assertEquals("FRONTEND", saved.getModule());
        assertEquals("JS_ERROR", saved.getOperation());
        assertEquals("fail", saved.getStatus());
        assertEquals("anonymous", saved.getUsername()); // 无认证上下文
        assertEquals("10.0.0.1", saved.getIpAddress());
        var node = objectMapper.readTree(saved.getAfterData());
        assertEquals("Cannot read properties of undefined", node.get("message").asText());
        assertTrue(node.get("stack").asText().startsWith("TypeError"));
        assertEquals("ERROR", node.get("level").asText());
    }

    @Test
    void WARN上报_记JS_warn级_不混进错误堆() {
        controller.report(req("WARN", "img 加载失败", null), request("10.0.0.2"));
        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogService).save(captor.capture());
        assertEquals("JS_WARN", captor.getValue().getOperation());
        assertEquals("warn", captor.getValue().getStatus());
    }

    @Test
    void 超长字段截断_不炸库() {
        String bigMessage = "m".repeat(ClientLogController.MAX_MESSAGE_LEN * 4);
        String bigStack = "s".repeat(ClientLogController.MAX_STACK_LEN * 2);
        controller.report(req("ERROR", bigMessage, bigStack), request("10.0.0.3"));

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogService).save(captor.capture());
        AuditLogEntity saved = captor.getValue();
        assertTrue(saved.getResponseResult().length() <= ClientLogController.MAX_MESSAGE_LEN + 20);
        assertTrue(saved.getResponseResult().endsWith("...(truncated)"));
        assertDoesNotThrow(() -> objectMapper.readTree(saved.getAfterData()));
    }

    @Test
    void 空报文静默成功_不写库() {
        assertEquals(200, controller.report(null, request("10.0.0.4")).getCode());
        assertEquals(200, controller.report(req("ERROR", "  ", null), request("10.0.0.4")).getCode());
        verify(auditLogService, never()).save(any());
    }

    @Test
    void 限流_每分钟超60条静默丢弃_且不回错误信号() {
        MockHttpServletRequest req = request("10.9.9.9");
        int accepted = 0;
        for (int i = 0; i < ClientLogController.RATE_LIMIT_PER_MIN + 40; i++) {
            R<Void> r = controller.report(req("ERROR", "err-" + i, null), req);
            if (r.getCode() == 200) accepted++; // 全部 200：不给探测者反馈
        }
        // 写库次数必须被封顶在限额（前 60 条），而非 100 条全进
        verify(auditLogService, times(ClientLogController.RATE_LIMIT_PER_MIN)).save(any());
        assertEquals(ClientLogController.RATE_LIMIT_PER_MIN + 40, accepted);
    }

    @Test
    void XFF优先_取第一跳() {
        MockHttpServletRequest req = request("172.18.0.1");
        req.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
        controller.report(req("ERROR", "x", null), req);
        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogService).save(captor.capture());
        assertEquals("203.0.113.7", captor.getValue().getIpAddress());
    }

    @Test
    void 转义_引号反斜杠换行_产出合法JSON() throws Exception {
        controller.report(req("ERROR", "bad\"path\\x\n换行", "s\"t\r\n"), request("10.0.0.5"));
        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogService).save(captor.capture());
        var node = objectMapper.readTree(captor.getValue().getAfterData());
        assertEquals("bad\"path\\x\n换行", node.get("message").asText());
    }
}
