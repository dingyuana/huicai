package com.huicai.sme.budget.service.impl;

import org.springframework.transaction.annotation.Transactional;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.budget.constant.BudgetStatus;
import com.huicai.sme.budget.entity.BudgetEntity;
import com.huicai.sme.budget.service.BudgetStateMachineService;
import org.springframework.stereotype.Service;

/**
 * 预算状态机服务实现.
 */
@Service
/**
 * 类级事务：P102/M5b —— 走租户表的读写路径必须有事务，否则切面不触发、
 * app.enterprise_id 设不进去，非超级用户下 RLS 会把本企业数据也过滤掉（读 0 行）。
 */
@Transactional
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