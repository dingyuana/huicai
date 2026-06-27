package com.huicai.module.ai.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.ai.constant.AiTaskStatus;
import com.huicai.module.ai.entity.AiTaskEntity;
import com.huicai.module.ai.service.AiTaskStateMachineService;
import org.springframework.stereotype.Service;

/**
 * AI 任务状态机服务实现.
 */
@Service
public class AiTaskStateMachineServiceImpl implements AiTaskStateMachineService {

    @Override
    public void assertProcessable(AiTaskEntity entity) {
        if (!AiTaskStatus.isProcessable(entity.getStatus())) {
            throw BusinessException.badRequest(
                    "AI任务当前状态 " + entity.getStatus() + " 不可启动处理, 需 PENDING");
        }
    }

    @Override
    public void assertCompletable(AiTaskEntity entity) {
        if (!AiTaskStatus.isCompletable(entity.getStatus())) {
            throw BusinessException.badRequest(
                    "AI任务当前状态 " + entity.getStatus() + " 不可完成, 需 PROCESSING");
        }
    }

    @Override
    public void assertFailable(AiTaskEntity entity) {
        if (!AiTaskStatus.isProcessing(entity.getStatus())) {
            throw BusinessException.badRequest(
                    "AI任务当前状态 " + entity.getStatus() + " 不可标记失败, 需 PROCESSING");
        }
    }

    @Override
    public void assertApplicable(AiTaskEntity entity) {
        if (!AiTaskStatus.isApplicable(entity.getStatus())) {
            throw BusinessException.badRequest(
                    "AI任务当前状态 " + entity.getStatus() + " 不可应用, 需 COMPLETED (人工确认)");
        }
    }

    @Override
    public void assertRejectable(AiTaskEntity entity) {
        if (!AiTaskStatus.isRejectable(entity.getStatus())) {
            throw BusinessException.badRequest(
                    "AI任务当前状态 " + entity.getStatus() + " 不可驳回, 需 COMPLETED");
        }
    }

    @Override
    public boolean isCompleted(AiTaskEntity entity) {
        return AiTaskStatus.isCompleted(entity.getStatus());
    }

    @Override
    public boolean isApplied(AiTaskEntity entity) {
        return AiTaskStatus.isApplied(entity.getStatus());
    }

    @Override
    public boolean isTerminal(AiTaskEntity entity) {
        return AiTaskStatus.isTerminal(entity.getStatus());
    }
}