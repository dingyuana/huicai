package com.huicai.sme.budget.dto;

import com.huicai.sme.budget.entity.BudgetAdjustmentEntity;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 预算调整单新增入参（REQ-2026-129 / P102 DTO 隔离 批次③）。
 *
 * <p>剔除 {@code status}（Service 固定置为 {@code ADJUSTMENT_PENDING}，
 * 审批走 {@code POST /adjustments/{id}/approve} 的人审端点）、
 * {@code approvedBy/approvedAt}（审批动作产生）、
 * {@code adjustmentNo}（若由前端生成则属业务编号，改由 Service 侧校验）、
 * 以及 {@code id/createdBy/createdAt/deleted}。
 *
 * <p>注：{@code gainLoss}、{@code usedAmount} 等金额派生字段一律不接受。
 */
@Data
public class BudgetAdjustmentCreateDTO {

    @NotNull(message = "预算 ID 不能为空")
    private Long budgetId;

    /** 调整类型：INCREASE / DECREASE 等（以 BudgetStatus 常量为准） */
    @Size(max = 32, message = "调整类型长度不能超过 32")
    @Pattern(regexp = "^$|INCREASE|DECREASE|TRANSFER|ADJUST",
            message = "调整类型仅支持 INCREASE / DECREASE / TRANSFER / ADJUST")
    private String adjustmentType;

    private LocalDate adjustmentDate;

    @Size(max = 16, message = "期间长度不能超过 16")
    private String period;

    @NotNull(message = "调整金额不能为空")
    private BigDecimal adjustmentAmount;

    @Size(max = 500, message = "调整原因过长")
    private String reason;

    public BudgetAdjustmentEntity toEntity() {
        BudgetAdjustmentEntity e = new BudgetAdjustmentEntity();
        e.setBudgetId(budgetId);
        e.setAdjustmentType(adjustmentType);
        e.setAdjustmentDate(adjustmentDate);
        e.setPeriod(period);
        e.setAdjustmentAmount(adjustmentAmount);
        e.setReason(reason);
        return e;
    }
}
