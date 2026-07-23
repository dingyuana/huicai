package com.huicai.base.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * AI 分类反馈日志实体
 * 对应 t_ai_feedback_log 表（V19）
 * append-only 用户行为日志，不可更新删除
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_ai_feedback_log")
public class AiFeedbackLogEntity extends BaseEntity {

    /** 租户 ID */
    private Long tenantId;

    /** 银行流水 ID（关联 t_bank_statement） */
    private Long bankTxnId;

    /** AI 建议分类 */
    private String aiSuggestedAction;

    /** AI 置信度 0-100 */
    private Integer aiConfidence;

    /** AI 业务场景 */
    private String aiBusinessScene;

    /** 人工操作: CONFIRM_AI / MANUAL_RECLASSIFY / IGNORE_AI / BATCH_CONFIRM */
    private String humanAction;

    /** 人工修改字段 JSONB */
    @TableField(value = "human_modified_fields", typeHandler = com.huicai.base.system.handler.JsonbTypeHandler.class)
    private String humanModifiedFields;

    /** 创建人 */
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
