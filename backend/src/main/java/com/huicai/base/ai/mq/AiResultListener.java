package com.huicai.base.ai.mq;

import com.huicai.base.ai.service.AiTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
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

    @RabbitListener(queues = "huicai.ai.result.queue")
    public void onResult(AiTaskResult result) {
        if (result == null || result.getTaskId() == null) {
            log.warn("收到空 AI 结果");
            return;
        }
        log.info("收到 AI 任务结果: taskId={}, status={}", result.getTaskId(), result.getStatus());
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
        }
    }
}
