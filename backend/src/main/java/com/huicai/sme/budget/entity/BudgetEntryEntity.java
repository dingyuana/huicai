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
    /**
     * 部门维度（V158 新增列，REQ-2026-126）.
     *
     * <p>⚠️ 历史缺口：本字段此前标 {@code @TableField(exist = false)} 且注释
     * 「DB 无此列」，实为 V158 才补建。标 exist=false 时赋值对 SQL 完全无效、
     * DB 读回必为 null，使部门维预算无法持久化。
     *
     * <p>不加 FK：t_dept 当前为空表（无种子数据），强 FK 会卡住测试与空库新装；
     * 维度合法性由业务端点/前端约束。
     */
    @TableField("dept_id")
    private Long deptId;
    /** 项目维度（V158 新增列，REQ-2026-126）。无 t_project 表，纯预留列。 */
    @TableField("project_id")
    private Long projectId;
    /**
     * 月份维度 1~12（V158 新增列，REQ-2026-126）.
     *
     * <p>与 t_budget_entry.period（VARCHAR(7)，历史遗留、当前无代码使用）
     * 并存：本字段是「预算编制到月」的粒度，不与 period 冲突。
     */
    @TableField("period_month")
    private Integer periodMonth;
    private BigDecimal amount;
    /**
     * 控制方式（V158 新增列，REQ-2026-126）.
     *
     * <p>CHECK {@code chk_budget_entry_control_type} 允许集：WARN/BLOCK/APPROVE，
     * 与 {@code BudgetServiceImpl.checkBudget} 的 switch 分支严格一致。
     * DB 默认 'WARN'，DEFAULT 保证历史行为（软提示）不变。
     */
    @TableField("control_type")
    private String controlType;
    /**
     * 已使用额（V158 新增列，REQ-2026-126）.
     *
     * <p>此前为幽灵字段，导致 {@code BudgetEntryMapper.addUsedAmount()} 的
     * {@code SET used_amount = used_amount + ...} 引用不存在的列而运行必报 SQL 错，
     * 且 executionAnalysis 汇总恒为 0。
     */
    @TableField("used_amount")
    private BigDecimal usedAmount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /**
     * ⚠️ 历史反向缺口（REQ-2026-126）：本字段此前误标 {@code exist = false}，
     * 而 {@code t_budget_entry.updated_at} 列**真实存在**（timestamp(6)）——
     * 与 {@code t_budget_adjustment} 对称。误标导致该列从不写入、读回恒 null。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
