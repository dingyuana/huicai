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
     * 预算已使用总额（t_budget.used_amount，NOT NULL DEFAULT 0）.
     *
     * <p>⚠️ 历史反向缺口（REQ-2026-126 修复）：该列**一直存在**，但 Entity
     * 从未声明对应字段，MyBatis-Plus 的 SELECT 永不回读它。头级使用额与
     * 条目级 used_amount 是两个层级，缺此字段会使头/条目口径无法互相校验。
     */
    private BigDecimal usedAmount;
    /**
     * 预算状态.
     * <p>可选值：{@code DRAFT}(草稿), {@code SUBMITTED}(已提交), {@code APPROVED}(已批准),
     * {@code ACTIVE}(执行中), {@code CLOSED}(已关闭), {@code REJECTED}(已驳回), {@code FROZEN}(已冻结)</p>
     */
    @StatusChangeable(entity = "BUDGET", fieldName = "status")
    private String status;
    /**
     * 审批人（V158 新增列，REQ-2026-126）.
     *
     * <p>⚠️ 历史缺口：此前标 {@code @TableField(exist = false)}，故
     * {@code approve()} 记录审批人完全无效。与 t_budget_adjustment 对称。
     */
    @TableField("approved_by")
    private Long approvedBy;
    /**
     * 审批时间（V158 新增列，REQ-2026-126）.
     *
     * <p>⚠️ 历史缺口：此前标 {@code @TableField(exist = false)}，而
     * {@code BudgetServiceImpl.approve()} 照常 {@code setApprovedAt(...)}
     * —— 幽灵字段赋值对 SQL 无效，DB 读回恒 null
     * （BudgetFlowE2ETest:119 据此实锤失败）。
     */
    @TableField("approved_at")
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
