package com.huicai.sme.budget.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.sme.budget.entity.BudgetAdjustmentEntity;
import com.huicai.sme.budget.entity.BudgetEntity;
import com.huicai.sme.budget.entity.BudgetEntryEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface BudgetService {
    IPage<BudgetEntity> pageQuery(String period, String status, Integer current, Integer size);
    BudgetEntity getById(Long id);
    BudgetEntity create(BudgetEntity entity, List<BudgetEntryEntity> entries);
    BudgetEntity submit(Long id);
    BudgetEntity approve(Long id);
    BudgetEntity activate(Long id);
    Map<String, Object> checkBudget(Long subjectId, String period, BigDecimal amount);
    Map<String, Object> executionAnalysis(String period);

    // 调整
    IPage<BudgetAdjustmentEntity> pageQueryAdjustment(String status, Integer current, Integer size);
    BudgetAdjustmentEntity createAdjustment(BudgetAdjustmentEntity entity);
    BudgetAdjustmentEntity approveAdjustment(Long id);
}
