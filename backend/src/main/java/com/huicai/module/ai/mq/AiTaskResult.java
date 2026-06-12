package com.huicai.module.ai.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * AI 任务结果回调
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiTaskResult implements Serializable {
    private Long taskId;
    private String taskNo;
    private String status;          // COMPLETED / FAILED
    private Map<String, Object> outputData;
    private Double confidence;
    private String errorMessage;
}
