package com.huicai.module.budget.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_budget_adjustment")
public class BudgetAdjustmentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String adjustmentNo;
    private Long budgetId;
    private String adjustmentType;
    private LocalDate adjustmentDate;
    private String period;
    private BigDecimal adjustmentAmount;
    private String reason;
    private String status;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
