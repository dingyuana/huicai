package com.huicai.base.ai.service;

import com.huicai.common.exception.BusinessException;
import com.huicai.base.ai.entity.AiTaskEntity;

/**
 * AI 任务状态机服务.
 * 封装 AI 任务 6 状态 (PENDING/PROCESSING/COMPLETED/FAILED/APPLIED/REJECTED) 的状态流转检查.
 * <p>
 * 核心铁律: AI 输出 = 建议，COMPLETED → APPLIED 必须由人工确认。
 * </p>
 */
public interface AiTaskStateMachineService {

    /**
     * 校验可启动处理 (PENDING → PROCESSING).
     *
     * @throws BusinessException 如果 status 不是 PENDING
     */
    void assertProcessable(AiTaskEntity entity);

    /**
     * 校验可完成 (PROCESSING → COMPLETED).
     *
     * @throws BusinessException 如果 status 不是 PROCESSING
     */
    void assertCompletable(AiTaskEntity entity);

    /**
     * 校验可标记失败 (PROCESSING → FAILED).
     *
     * @throws BusinessException 如果 status 不是 PROCESSING
     */
    void assertFailable(AiTaskEntity entity);

    /**
     * 校验可应用 (COMPLETED → APPLIED).
     * 必须由人工确认后调用。
     *
     * @throws BusinessException 如果 status 不是 COMPLETED
     */
    void assertApplicable(AiTaskEntity entity);

    /**
     * 校验可驳回 (COMPLETED → REJECTED).
     *
     * @throws BusinessException 如果 status 不是 COMPLETED
     */
    void assertRejectable(AiTaskEntity entity);

    /**
     * 检查是否为已完成状态（待人工确认）。
     */
    boolean isCompleted(AiTaskEntity entity);

    /**
     * 检查是否为已应用状态。
     */
    boolean isApplied(AiTaskEntity entity);

    /**
     * 检查是否为终态。
     */
    boolean isTerminal(AiTaskEntity entity);
}