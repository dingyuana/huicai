package com.huicai.sme.budget.dto.vo;

import com.huicai.sme.budget.entity.BudgetAdjustmentEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预算调整出参 VO（P140 出参面 VO 化批次17）。
 * 剔除服务端托管字段（deleted / enterpriseId / version / updatedBy）。
 */
@Data
public class BudgetAdjustmentVO {

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
    private LocalDateTime createdAt;

    public static BudgetAdjustmentVO from(BudgetAdjustmentEntity e) {
        BudgetAdjustmentVO vo = new BudgetAdjustmentVO();
        vo.setId(e.getId());
        vo.setAdjustmentNo(e.getAdjustmentNo());
        vo.setBudgetId(e.getBudgetId());
        vo.setAdjustmentType(e.getAdjustmentType());
        vo.setAdjustmentDate(e.getAdjustmentDate());
        vo.setPeriod(e.getPeriod());
        vo.setAdjustmentAmount(e.getAdjustmentAmount());
        vo.setReason(e.getReason());
        vo.setStatus(e.getStatus());
        vo.setApprovedBy(e.getApprovedBy());
        vo.setApprovedAt(e.getApprovedAt());
        vo.setCreatedBy(e.getCreatedBy());
        vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }
}
