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

    /**
     * SPEC-P139：下发时携带当前账套企业。回调结果须经 Python 原样透传，
     * 监听线程据此重建 {@code EnterpriseContextHolder}——否则 MQ 线程下
     * GUC 为 NULL ⇒ RLS 谓词过滤所有行 ⇒ 写回被静默丢弃（§0.1 生产探针坐实）。
     */
    private Long enterpriseId;
}
