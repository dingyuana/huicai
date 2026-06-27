package com.huicai.module.budget.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.budget.constant.BudgetStatus;
import com.huicai.module.budget.entity.BudgetAdjustmentEntity;
import com.huicai.module.budget.service.BudgetAdjustmentStateMachineService;
import org.springframework.stereotype.Service;

/**
 * 预算调整状态机服务实现.
 */
@Service
public class BudgetAdjustmentStateMachineServiceImpl implements BudgetAdjustmentStateMachineService {

    @Override
    public void assertApprovable(BudgetAdjustmentEntity entity) {
        if (!BudgetStatus.isAdjustmentApprovable(entity.getStatus())) {
            throw BusinessException.badRequest(
                    "预算调整当前状态 " + entity.getStatus() + " 不可批准, 需 PENDING");
        }
    }

    @Override
    public void assertExecutable(BudgetAdjustmentEntity entity) {
        if (!BudgetStatus.isAdjustmentExecutable(entity.getStatus())) {
            throw BusinessException.badRequest(
                    "预算调整当前状态 " + entity.getStatus() + " 不可执行, 需 APPROVED");
        }
    }

    @Override
    public boolean isApproved(BudgetAdjustmentEntity entity) {
        return BudgetStatus.isAdjustmentApproved(entity.getStatus());
    }

    @Override
    public boolean isExecuted(BudgetAdjustmentEntity entity) {
        return BudgetStatus.isAdjustmentExecuted(entity.getStatus());
    }

    @Override
    public boolean isModifiable(BudgetAdjustmentEntity entity) {
        return BudgetStatus.isAdjustmentModifiable(entity.getStatus());
    }
}