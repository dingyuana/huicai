package com.huicai.base.ai.mq;

import com.huicai.base.ai.service.AiTaskService;
import com.huicai.base.ai.config.AiRabbitConfig;
import com.huicai.common.context.EnterpriseContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * SPEC-P139：MQ 回调链路租户上下文语义断言（§5）。用「在桩内读 ThreadLocal」
 * 验证监听线程在调用 {@code AiTaskService#handleResult} 时刻的上下文状态——
 * 这比结构守卫更能防「代码写过但没生效」类漂移（§4.5 第 61 条判据①）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiResultListenerContextTest {

    private static final long ENT = 42L;

    @Mock
    private AiTaskService aiTaskService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private AiResultListener listener;

    private final AtomicReference<Long> contextAtCall = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        listener = new AiResultListener(aiTaskService, rabbitTemplate);
        // 桩内记录「调用时刻」的上下文——onResult 用的是同一根线程
        doAnswer(inv -> {
            contextAtCall.set(EnterpriseContextHolder.get());
            return null;
        }).when(aiTaskService).handleResult(any(), any(), anyMap(), any(), any());
    }

    @AfterEach
    void tearDown() {
        EnterpriseContextHolder.clear();
    }

    private AiTaskResult result(Long enterpriseId) {
        AiTaskResult r = new AiTaskResult();
        r.setTaskId(3L);
        r.setTaskNo("TASK-139");
        r.setStatus("COMPLETED");
        r.setOutputData(java.util.Map.of("probe", "P139"));
        r.setConfidence(0.99);
        r.setEnterpriseId(enterpriseId);
        return r;
    }

    /** AT-139-1：Given 消息携带 enterpriseId，When 执行到 handleResult 调用时刻，Then 上下文 == 该值 */
    @Test
    void restoresContextFromMessageBeforeWrite() {
        listener.onResult(result(ENT));

        assertEquals(ENT, contextAtCall.get(),
                "监听线程必须在调用 handleResult 之前按消息重建租户上下文");
        verify(aiTaskService).handleResult(any(), any(), anyMap(), any(), any());
    }

    /** AT-139-2：Given handleResult 抛异常，Then finally 后上下文必须被清除（防连接池/线程复用污染） */
    @Test
    void clearsContextAfterHandlingEvenOnError() {
        // ⚠️ 一个桩内同时「记录 + 抛出」：doThrow 会整体覆盖 setUp 的 doAnswer，
        //    导致异常路径没有记录调用时刻上下文（曾因此假红）。
        doAnswer(inv -> {
            contextAtCall.set(EnterpriseContextHolder.get());
            throw new RuntimeException("boom");
        }).when(aiTaskService).handleResult(any(), any(), anyMap(), any(), any());

        listener.onResult(result(ENT)); // onResult catch + finally，不应抛出

        assertEquals(ENT, contextAtCall.get(), "异常路径同样应先设置上下文再写回");
        assertNull(EnterpriseContextHolder.get(),
                "finally 必须清理上下文——残留会污染连接池上的下一事务（§4.5 第 29 条）");
    }

    /** AT-139-5：Given 消息无 enterpriseId，Then 不得静默丢写 + 上下文绝不能被误置 */
    @Test
    void missingEnterpriseIdFollowsDeclaredPolicy() {
        AiTaskResult r = result(null);
        listener.onResult(r);

        verify(aiTaskService, never()).handleResult(any(), any(), anyMap(), any(), any());
        assertNull(EnterpriseContextHolder.get(), "无租户消息不得设置任何上下文");
        // D-2 裁定：显式转发 DLQ，不静默 ack 丢弃
        ArgumentCaptor<Object> dlqCaptor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(
                eq(AiRabbitConfig.DLQ_EXCHANGE), eq(AiRabbitConfig.DLQ_ROUTING_KEY), dlqCaptor.capture());
        assertSame(r, dlqCaptor.getValue(), "DLQ 里必须是原始结果消息，供人工按 taskId 追溯重放");
    }
}
