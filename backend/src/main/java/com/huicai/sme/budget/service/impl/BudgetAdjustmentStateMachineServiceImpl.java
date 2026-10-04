package com.huicai.sme.budget.service.impl;

import org.springframework.transaction.annotation.Transactional;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.budget.constant.BudgetStatus;
import com.huicai.sme.budget.entity.BudgetAdjustmentEntity;
import com.huicai.sme.budget.service.BudgetAdjustmentStateMachineService;
import org.springframework.stereotype.Service;

/**
 * 预算调整状态机服务实现.
 */
@Service
/**
 * 类级事务：P102/M5b —— 走租户表的读写路径必须有事务，否则切面不触发、
 * app.enterprise_id 设不进去，非超级用户下 RLS 会把本企业数据也过滤掉（读 0 行）。
 */
@Transactional
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