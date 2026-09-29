package com.huicai.sme.budget.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_budget")
public class BudgetEntity extends BaseEntity {

    private String budgetNo;

    /**
     * 预算名称（t_budget.budget_name，NOT NULL 无默认值）.
     *
     * <p>⚠️ 历史缺口（REQ-2026-121 修复）：本字段此前<b>完全不存在</b>，
     * 而 DB 列是 NOT NULL，导致 {@code BudgetMapper.insert()} 必然报
     * {@code null value in column "budget_name"}。
     */
    @TableField("budget_name")
    private String budgetName;

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
