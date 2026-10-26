package com.huicai.base.ai.mq;

import com.huicai.base.ai.service.AiTaskService;
import com.huicai.base.ai.config.AiRabbitConfig;
import com.huicai.common.context.EnterpriseContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 监听 AI 服务回调结果
 * 队列: huicai.ai.result.queue
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class AiResultListener {

    private final AiTaskService aiTaskService;

    // SPEC-P139 D-2：无 enterpriseId 的消息须显式转发 DLQ（不静默、不无限重投）
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = "huicai.ai.result.queue")
    public void onResult(AiTaskResult result) {
        if (result == null || result.getTaskId() == null) {
            log.warn("收到空 AI 结果");
            return;
        }
        log.info("收到 AI 任务结果: taskId={}, status={}", result.getTaskId(), result.getStatus());
        // SPEC-P139 C-04/D-2：无 enterpriseId ⇒ MQ 线程无租户上下文 ⇒ RLS 过滤所有行 ⇒
        // 旧实现在这里静默丢写（生产探针坐实）。按裁定：log.error + 显式转 DLQ，不 ack 丢弃、不无限重投。
        if (result.getEnterpriseId() == null) {
            log.error("AI 结果缺少 enterpriseId, 无法写回(RLS 将过滤 0 行), 显式转入 DLQ: taskId={}, taskNo={}",
                    result.getTaskId(), result.getTaskNo());
            forwardToDlq(result);
            return;
        }
        // SPEC-P139 C-03：调用 handleResult 前重建租户上下文；finally 必清（污染源自收，§4.5 第 29 条）
        EnterpriseContextHolder.set(result.getEnterpriseId());
        try {
            Map<String, Object> output = result.getOutputData();
            aiTaskService.handleResult(
                    result.getTaskId(),
                    result.getStatus(),
                    output,
                    result.getConfidence(),
                    result.getErrorMessage()
            );
        } catch (Exception e) {
            log.error("处理 AI 结果失败: taskId={}", result.getTaskId(), e);
        } finally {
            EnterpriseContextHolder.clear();
        }
    }

    private void forwardToDlq(AiTaskResult result) {
        try {
            rabbitTemplate.convertAndSend(AiRabbitConfig.DLQ_EXCHANGE, AiRabbitConfig.DLQ_ROUTING_KEY, result);
            log.info("AI 结果已转 DLQ: taskId={}", result.getTaskId());
        } catch (Exception e) {
            log.error("AI 结果转 DLQ 失败: taskId={}", result.getTaskId(), e);
        }
    }
}
