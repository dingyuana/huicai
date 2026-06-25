package com.huicai.module.budget.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_budget")
public class BudgetEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String budgetNo;
    private String period;
    private String budgetType;
    private BigDecimal totalAmount;
    @StatusChangeable(entity = "BUDGET", fieldName = "status")
    private String status;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private String remark;
    private Long createdBy;
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
