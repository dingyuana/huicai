package com.huicai.base.ai.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * AI 任务消息载体(RabbitMQ 消息体)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiTaskMessage implements Serializable {
    private Long taskId;
    private String taskNo;
    private String taskType;
    private String bizType;
    private Long bizId;
    private Map<String, Object> inputData;
    private String callbackQueue;
}
