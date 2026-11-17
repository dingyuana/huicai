package com.huicai.sme.budget.dto.vo;

import com.huicai.sme.budget.entity.BudgetEntity;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 预算主表出参 VO（P140 出参面 VO 化批次17）。
 * 对应前端 {@code Budget} interface，剔除服务端托管字段（deleted / enterpriseId / version / updatedBy）。
 */
@Data
public class BudgetVO {

    private Long id;
    private String budgetNo;
    private String period;
    private String budgetType;
    private BigDecimal totalAmount;
    private String status;
    private String remark;

    public static BudgetVO from(BudgetEntity e) {
        BudgetVO vo = new BudgetVO();
        vo.setId(e.getId());
        vo.setBudgetNo(e.getBudgetNo());
        vo.setPeriod(e.getPeriod());
        vo.setBudgetType(e.getBudgetType());
        vo.setTotalAmount(e.getTotalAmount());
        vo.setStatus(e.getStatus());
        vo.setRemark(e.getRemark());
        return vo;
    }
}
