package com.huicai.base.ai.service;

import com.huicai.base.ai.entity.AiTaskEntity;
import com.huicai.base.ai.mapper.AiAnomalyTagMapper;
import com.huicai.base.ai.mapper.AiTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SPEC-P139 实施中查出的连带缺陷：旧实现用 {@code outputData.toString()}
 * 生成 Map.toString 格式（{@code {k=v}}），不是 JSON —— JsonbTypeHandler 解析失败后
 * <b>静默落 {}</b>（handler 里 catch 后 setValue("{}")），AI 结果正文全丢且无任何报错。
 * 修复：经 Jackson 序列化为合法 JSON（§4.3 第 10 条静默失败反模式的再现实例）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiTaskHandleResultJsonTest {

    @Mock
    private AiTaskMapper taskMapper;

    @Mock
    private AiAnomalyTagMapper anomalyMapper;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void handleResultSerializesOutputDataAsJson() {
        AiTaskService service = new AiTaskService(taskMapper, anomalyMapper, rabbitTemplate,
                new com.fasterxml.jackson.databind.ObjectMapper());
        AiTaskEntity existing = new AiTaskEntity();
        existing.setId(3L);
        existing.setStatus("PENDING");
        when(taskMapper.selectById(3L)).thenReturn(existing);

        service.handleResult(3L, "COMPLETED", Map.of("probe", "P139-JSON"), 0.9, null);

        ArgumentCaptor<AiTaskEntity> captor = ArgumentCaptor.forClass(AiTaskEntity.class);
        verify(taskMapper).updateById(captor.capture());
        assertEquals("{\"probe\":\"P139-JSON\"}", captor.getValue().getOutputData(),
                "outputData 必须序列化为 jsonb 可解析的 JSON 反序列化安全格式；"
                        + "Map.toString 会被 JsonbTypeHandler 静默替换成 {}");
    }
}
