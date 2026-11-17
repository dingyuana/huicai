package com.huicai.common.exception;

import com.huicai.common.response.R;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SPEC-P152：兜底异常必须<b>发布留痕事件</b>。
 *
 * <p>handler 只依赖 {@code ApplicationEventPublisher}（Spring 核心设施）—— 这是
 * {@code @WebMvcTest} 切片兼容的前提：advice 被切片自动装配，若依赖服务层 bean
 * 全部 WebMvc 切片会起不来（SPEC-P152 实测 97 个契约测试打挂）。落库行为
 * 由 {@code ServerErrorAuditListenerTest} 单独守。</p>
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerServerErrorAuditTest {

    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(eventPublisher);
        SecurityContextHolder.clearContext();
    }

    @Test
    void 兜底异常_返回500且发布事件_携带请求上下文与操作人() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/report/balance");
        request.setRemoteAddr("10.1.2.3");
        request.addHeader("User-Agent", "junit-agent");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("accountant", "n/a", List.of()));

        R<Void> r = handler.handleUnknown(new IllegalStateException("余额计算炸了"), request);

        assertEquals(500, r.getCode());
        ArgumentCaptor<ServerErrorEvent> captor = ArgumentCaptor.forClass(ServerErrorEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ServerErrorEvent event = captor.getValue();
        assertEquals("accountant", event.getUsername());
        assertEquals("GET", event.getHttpMethod());
        assertEquals("/api/v1/report/balance", event.getUri());
        assertEquals("10.1.2.3", event.getClientIp());
        assertEquals("junit-agent", event.getUserAgent());
        assertEquals("余额计算炸了", event.getException().getMessage());
    }

    @Test
    void 无认证上下文_事件记anonymous() {
        handler.handleUnknown(new RuntimeException("x"), new MockHttpServletRequest());
        ArgumentCaptor<ServerErrorEvent> captor = ArgumentCaptor.forClass(ServerErrorEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals("anonymous", captor.getValue().getUsername());
    }

    @Test
    void 事件发布失败_仍返回500_不抛二次异常() {
        doThrow(new RuntimeException("总线炸了")).when(eventPublisher).publishEvent(any(Object.class));
        R<Void> r = assertDoesNotThrow(() ->
                handler.handleUnknown(new RuntimeException("x"), new MockHttpServletRequest()));
        assertEquals(500, r.getCode());
    }

    @Test
    void request为null_事件字段缺省不炸() {
        handler.handleUnknown(new RuntimeException("x"), null);
        ArgumentCaptor<ServerErrorEvent> captor = ArgumentCaptor.forClass(ServerErrorEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertNull(captor.getValue().getUri());
    }
}
