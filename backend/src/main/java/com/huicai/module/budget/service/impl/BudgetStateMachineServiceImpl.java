package com.huicai.module.budget.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.budget.constant.BudgetStatus;
import com.huicai.module.budget.entity.BudgetEntity;
import com.huicai.module.budget.service.BudgetStateMachineService;
import org.springframework.stereotype.Service;

/**
 * 预算状态机服务实现.
 */
@Service
public class BudgetStateMachineServiceImpl implements BudgetStateMachineService {

    @Override
    public void assertSubmittable(BudgetEntity entity) {
        if (!BudgetStatus.isBudgetSubmittable(entity.getStatus())) {
            throw BusinessException.badRequest(
                    "预算当前状态 " + entity.getStatus() + " 不可提交, 需 DRAFT");
        }
    }

    @Override
    public void assertApprovable(BudgetEntity entity) {
        if (!BudgetStatus.isBudgetApprovable(entity.getStatus())) {
            throw BusinessException.badRequest(
                    "预算当前状态 " + entity.getStatus() + " 不可批准, 需 SUBMITTED");
        }
    }

    @Override
    public void assertFreezable(BudgetEntity entity) {
        if (!BudgetStatus.isBudgetFreezable(entity.getStatus())) {
            throw BusinessException.badRequest(
                    "预算当前状态 " + entity.getStatus() + " 不可冻结, 需 APPROVED");
        }
    }

    @Override
    public boolean isApproved(BudgetEntity entity) {
        return BudgetStatus.isBudgetApproved(entity.getStatus());
    }

    @Override
    public boolean isFrozen(BudgetEntity entity) {
        return BudgetStatus.isBudgetFrozen(entity.getStatus());
    }

    @Override
    public boolean isModifiable(BudgetEntity entity) {
        return BudgetStatus.isBudgetModifiable(entity.getStatus());
    }
}