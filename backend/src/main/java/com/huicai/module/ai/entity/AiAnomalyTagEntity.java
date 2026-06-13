package com.huicai.module.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_ai_anomaly_tag")
public class AiAnomalyTagEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String bizType;
    private Long bizId;
    private String anomalyType;
    private String severity;
    private String description;
    private Long aiTaskId;
    private Boolean resolved;
    private Long resolvedBy;
    private LocalDateTime resolvedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
