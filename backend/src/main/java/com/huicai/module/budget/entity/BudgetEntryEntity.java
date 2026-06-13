package com.huicai.module.budget.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_budget_entry")
public class BudgetEntryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long budgetId;
    private Long subjectId;
    private Long deptId;
    private Long projectId;
    private Integer periodMonth;
    private BigDecimal amount;
    private String controlType;
    private BigDecimal usedAmount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
