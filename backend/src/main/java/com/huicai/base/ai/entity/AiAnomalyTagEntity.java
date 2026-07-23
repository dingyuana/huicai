package com.huicai.base.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_ai_anomaly_tag")
public class AiAnomalyTagEntity extends BaseEntity {

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
