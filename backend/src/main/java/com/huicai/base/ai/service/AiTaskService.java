package com.huicai.base.ai.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.context.EnterpriseContextHolder;
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
// 🔴 类级事务（SPEC-P128 §7.1 同型缺陷修复）：t_ai_task 是 RLS 租户表
//   （ENABLE + FORCE，谓词 enterprise_id = app.enterprise_id），而读路径若无事务，
//   TenantGucSetter#apply 会因 !isActualTransactionActive() 提前 return
//   ⇒ GUC 恒 NULL ⇒ 谓词取 NULL ⇒ **整表被过滤**。
//   实测症状（服务器 2026-10-18）：DB 有 3 行时
//   GET /api/v1/ai/tasks/page 返回 total=0、GET /api/v1/ai/tasks/{id} 报「AI 任务不存在」。
//   本类原先只有 4 个写方法各自挂了 @Transactional，唯独 getById 等读方法裸奔。
@Transactional(rollbackFor = Exception.class)
public class AiTaskService {

    private final AiTaskMapper taskMapper;
    private final AiAnomalyTagMapper anomalyMapper;
    private final RabbitTemplate rabbitTemplate;

    /**
     * SPEC-P139 连带缺陷修复：outputData 附 jsonb 列（JsonbTypeHandler 断言合法 JSON），
     * 旧实现 {@code outputData.toString()} 生成 Map.toString 格式（{k=v}）⇒ handler
     * catch 后<b>静默落 {}</b>，AI 结果正文全丢。必须经 Jackson 序列化。
     */
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

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
        // SPEC-P139 C-01：回调链路无租户上下文 ⇒ GUC NULL ⇒ RLS 过滤 ⇒ 静默丢写；
        // 企业必须随消息全链路透传（下发时取 web 线程上下文；Python 原样回传）
        message.setEnterpriseId(EnterpriseContextHolder.get());

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
            // SPEC-P139：此处上下文已在场（监听层已重建），能查到就能查到——
            // 真不存在（已删除/传错）才走这里，文案必须与「RLS 过滤」的假象区分开
            log.warn("AI 结果写回失败: 任务不存在(可能已删除或传错 taskId), taskId={}", taskId);
            return;
        }
        task.setStatus(status);
        try {
            task.setOutputData(outputData == null ? null : objectMapper.writeValueAsString(outputData));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // fail-loud（探针已坐实 handler 吞错 ⇒ 静默丢正文的代价）；非法入参必须显式可见
            throw new BusinessException("AI 结果 outputData 序列化失败: " + e.getMessage());
        }
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
