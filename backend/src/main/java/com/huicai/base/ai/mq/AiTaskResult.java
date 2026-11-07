package com.huicai.base.ai.mq;

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

    /**
     * SPEC-P139：回调结果须携带目标账套企业（Python 侧从下发消息透传）。
     * 监听线程据此在写回前重建租户上下文；缺失时按 D-2 兜底（log.error + DLQ），
     * 不得静默丢弃。
     */
    private Long enterpriseId;
}
