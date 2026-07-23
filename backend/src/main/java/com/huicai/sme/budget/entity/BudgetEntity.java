package com.huicai.sme.budget.entity;

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
    /**
     * 预算状态.
     * <p>可选值：{@code DRAFT}(草稿), {@code SUBMITTED}(已提交), {@code APPROVED}(已批准),
     * {@code ACTIVE}(执行中), {@code CLOSED}(已关闭), {@code REJECTED}(已驳回), {@code FROZEN}(已冻结)</p>
     */
    @StatusChangeable(entity = "BUDGET", fieldName = "status")
    private String status;
    @TableField(exist = false)
    private Long approvedBy;
    @TableField(exist = false)
    private LocalDateTime approvedAt;
    private String remark;
    private Long createdBy;
    @TableField(exist = false)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
