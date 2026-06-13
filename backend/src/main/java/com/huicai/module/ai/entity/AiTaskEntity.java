package com.huicai.module.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_ai_task")
public class AiTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskNo;
    private String taskType;
    private String bizType;
    private Long bizId;
    private String status;
    private String inputData;
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
