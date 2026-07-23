package com.huicai.sme.budget.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_budget_adjustment")
public class BudgetAdjustmentEntity extends BaseEntity {

    private String adjustmentNo;
    private Long budgetId;
    private String adjustmentType;
    private LocalDate adjustmentDate;
    private String period;
    private BigDecimal adjustmentAmount;
    private String reason;
    @StatusChangeable(entity = "BUDGET_ADJUSTMENT", fieldName = "status")
    private String status;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
