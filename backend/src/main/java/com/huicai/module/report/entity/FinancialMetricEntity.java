package com.huicai.module.report.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_financial_metric")
public class FinancialMetricEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String metricCode;
    private String metricName;
    private String category;
    private String formula;
    private String unit;
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
