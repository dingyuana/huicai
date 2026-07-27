package com.huicai.sme.budget.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_budget_entry")
public class BudgetEntryEntity extends BaseEntity {

    private Long budgetId;
    private Long subjectId;
    /** DB 无此列 */
    @TableField(exist = false)
    private Long deptId;
    /** DB 无此列 */
    @TableField(exist = false)
    private Long projectId;
    /** DB 无此列（DB 用 period VARCHAR） */
    @TableField(exist = false)
    private Integer periodMonth;
    private BigDecimal amount;
    @TableField(exist = false)
    private String controlType;
    @TableField(exist = false)
    private BigDecimal usedAmount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE, exist = false)
    private LocalDateTime updatedAt;
}
