package com.huicai.base.ai.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.base.ai.config.AiRabbitConfig;
import com.huicai.base.ai.entity.AiAnomalyTagEntity;
import com.huicai.base.ai.entity.AiTaskEntity;
import com.huicai.base.ai.mapper.AiAnomalyTagMapper;
import com.huicai.base.ai.mapper.AiTaskMapper;
import com.huicai.base.ai.mq.AiTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiTaskService {

    private final AiTaskMapper taskMapper;
    private final AiAnomalyTagMapper anomalyMapper;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 创建并下发 AI 任务
     */
    @Transactional(rollbackFor = Exception.class)
    public AiTaskEntity createAndDispatch(String taskType, String bizType, Long bizId,
                                           Map<String, Object> inputData) {
        AiTaskEntity task = new AiTaskEntity();
        task.setTaskNo("AI" + System.currentTimeMillis() + IdUtil.fastSimpleUUID().substring(0, 4).toUpperCase());
        task.setTaskType(taskType);
        task.setBizType(bizType);
        task.setBizId(bizId);
        task.setStatus("PENDING");
        task.setInputData(inputData == null ? null : inputData.toString());
        task.setReviewed(false);
        task.setApplyStatus("NOT_APPLIED");
        taskMapper.insert(task);

        // 下发到 RabbitMQ
        AiTaskMessage message = new AiTaskMessage();
        message.setTaskId(task.getId());
        message.setTaskNo(task.getTaskNo());
        message.setTaskType(taskType);
        message.setBizType(bizType);
        message.setBizId(bizId);
        message.setInputData(inputData);
        message.setCallbackQueue(AiRabbitConfig.RESULT_ROUTING_KEY);

        try {
            rabbitTemplate.convertAndSend(
                    AiRabbitConfig.AI_EXCHANGE,
                    AiRabbitConfig.TASK_ROUTING_KEY,
                    message
            );
            log.info("AI 任务已下发: taskNo={}, type={}", task.getTaskNo(), taskType);
        } catch (Exception e) {
            log.error("AI 任务下发失败, 标记为失败状态: {}", e.getMessage());
            task.setStatus("FAILED");
            task.setErrorMessage("下发失败: " + e.getMessage());
            taskMapper.updateById(task);
        }
        return task;
    }

    /**
     * 处理 AI 回调结果
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleResult(Long taskId, String status, Map<String, Object> outputData,
                              Double confidence, String errorMessage) {
        AiTaskEntity task = taskMapper.selectById(taskId);
        if (task == null) {
            log.warn("AI 任务不存在: taskId={}", taskId);
            return;
        }
        task.setStatus(status);
        task.setOutputData(outputData == null ? null : outputData.toString());
        task.setConfidence(confidence == null ? null : BigDecimal.valueOf(confidence));
        task.setErrorMessage(errorMessage);
        task.setStartedAt(LocalDateTime.now());
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    /**
     * 人工审核并应用结果
     */
    @Transactional(rollbackFor = Exception.class)
    public AiTaskEntity review(Long taskId, Long reviewerId, boolean approved) {
        AiTaskEntity task = taskMapper.selectById(taskId);
        if (task == null) throw new BusinessException("AI 任务不存在");
        if (!"COMPLETED".equals(task.getStatus())) {
            throw new BusinessException("仅已完成任务可审核");
        }
        task.setReviewed(true);
        task.setReviewedBy(reviewerId);
        task.setReviewedAt(LocalDateTime.now());
        task.setApplyStatus(approved ? "APPLIED" : "REJECTED");
        taskMapper.updateById(task);
        return task;
    }

    public IPage<AiTaskEntity> pageQuery(String taskType, String status, Integer current, Integer size) {
        Page<AiTaskEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<AiTaskEntity> wrapper = new LambdaQueryWrapper<>();
        if (taskType != null && !taskType.isBlank()) {
            wrapper.eq(AiTaskEntity::getTaskType, taskType);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(AiTaskEntity::getStatus, status);
        }
        wrapper.orderByDesc(AiTaskEntity::getCreatedAt);
        return taskMapper.selectPage(page, wrapper);
    }

    public AiTaskEntity getById(Long id) {
        AiTaskEntity entity = taskMapper.selectById(id);
        if (entity == null) throw new BusinessException("AI 任务不存在");
        return entity;
    }

    /**
     * 标记异常
     */
    public AiAnomalyTagEntity tagAnomaly(String bizType, Long bizId, String anomalyType,
                                          String severity, String description, Long taskId) {
        AiAnomalyTagEntity tag = new AiAnomalyTagEntity();
        tag.setBizType(bizType);
        tag.setBizId(bizId);
        tag.setAnomalyType(anomalyType);
        tag.setSeverity(severity);
        tag.setDescription(description);
        tag.setAiTaskId(taskId);
        tag.setResolved(false);
        anomalyMapper.insert(tag);
        return tag;
    }

    /**
     * 异常列表
     */
    public List<AiAnomalyTagEntity> listAnomalies(String bizType, Boolean resolved) {
        LambdaQueryWrapper<AiAnomalyTagEntity> wrapper = new LambdaQueryWrapper<>();
        if (bizType != null) wrapper.eq(AiAnomalyTagEntity::getBizType, bizType);
        if (resolved != null) wrapper.eq(AiAnomalyTagEntity::getResolved, resolved);
        wrapper.orderByDesc(AiAnomalyTagEntity::getCreatedAt);
        return anomalyMapper.selectList(wrapper);
    }

    /**
     * 解决异常
     */
    @Transactional(rollbackFor = Exception.class)
    public AiAnomalyTagEntity resolveAnomaly(Long id, Long resolverId) {
        AiAnomalyTagEntity tag = anomalyMapper.selectById(id);
        if (tag == null) throw new BusinessException("异常记录不存在");
        tag.setResolved(true);
        tag.setResolvedBy(resolverId);
        tag.setResolvedAt(LocalDateTime.now());
        anomalyMapper.updateById(tag);
        return tag;
    }
}
