package com.huicai.base.ai.mq;

import com.huicai.base.ai.config.AiRabbitConfig;
import com.huicai.base.ai.mapper.AiAnomalyTagMapper;
import com.huicai.base.ai.mapper.AiTaskMapper;
import com.huicai.base.ai.service.AiTaskService;
import com.huicai.common.context.EnterpriseContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * SPEC-P139 AT-139-3：下发消息必须携带当前上下文的 enterpriseId（Python 回传的源头）。
 * 摘掉 {@code createAndDispatch} 里的 {@code setEnterpriseId} ⇒ 本例转红（反证 F-3）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiTaskDispatchContextTest {

    private static final long ENT = 7L;

    @Mock
    private AiTaskMapper taskMapper;

    @Mock
    private AiAnomalyTagMapper anomalyMapper;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @AfterEach
    void tearDown() {
        EnterpriseContextHolder.clear();
    }

    @Test
    void dispatchMessageCarriesContextEnterprise() {
        AiTaskService service = new AiTaskService(taskMapper, anomalyMapper, rabbitTemplate,
                new com.fasterxml.jackson.databind.ObjectMapper());
        EnterpriseContextHolder.set(ENT);
        try {
            service.createAndDispatch("OCR", "VOUCHER", 1L, new HashMap<>());

            ArgumentCaptor<AiTaskMessage> captor = ArgumentCaptor.forClass(AiTaskMessage.class);
            verify(rabbitTemplate).convertAndSend(
                    eq(AiRabbitConfig.AI_EXCHANGE), eq(AiRabbitConfig.TASK_ROUTING_KEY), captor.capture());
            assertEquals(ENT, captor.getValue().getEnterpriseId(),
                    "下发消息必须携带当前账套企业，否则回调链路无法重建租户上下文");
        } finally {
            EnterpriseContextHolder.clear();
        }
    }
}
