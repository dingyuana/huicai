package com.huicai.module.report.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_cash_flow_rule")
public class CashFlowRuleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    private String name;
    private String flowType;
    private String matchSubject;
    private String flowItem;
    private Integer priority;
    private Boolean isActive;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
