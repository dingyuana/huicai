package com.huicai.base.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_ai_task")
public class AiTaskEntity extends BaseEntity {

    private String taskNo;
    private String taskType;
    private String bizType;
    private Long bizId;
    @StatusChangeable(entity = "AI_TASK", fieldName = "status")
    private String status;
    @TableField(value = "input_data", typeHandler = com.huicai.base.system.handler.JsonbTypeHandler.class)
    private String inputData;
    @TableField(value = "output_data", typeHandler = com.huicai.base.system.handler.JsonbTypeHandler.class)
    private String outputData;
    private String errorMessage;
    private java.math.BigDecimal confidence;
    private Boolean reviewed;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String applyStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @TableLogic
    private Integer deleted;
}
