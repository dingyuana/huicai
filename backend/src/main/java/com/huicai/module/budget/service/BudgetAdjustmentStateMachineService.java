package com.huicai.module.budget.service;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.budget.entity.BudgetAdjustmentEntity;

/**
 * 预算调整状态机服务.
 * 封装预算调整 3 状态 (PENDING/APPROVED/EXECUTED) 的状态流转检查.
 */
public interface BudgetAdjustmentStateMachineService {

    /**
     * 校验可批准 (PENDING → APPROVED).
     *
     * @throws BusinessException 如果 status 不是 PENDING
     */
    void assertApprovable(BudgetAdjustmentEntity entity);

    /**
     * 校验可执行 (APPROVED → EXECUTED).
     *
     * @throws BusinessException 如果 status 不是 APPROVED
     */
    void assertExecutable(BudgetAdjustmentEntity entity);

    /**
     * 检查是否为已批准状态.
     */
    boolean isApproved(BudgetAdjustmentEntity entity);

    /**
     * 检查是否为已执行状态.
     */
    boolean isExecuted(BudgetAdjustmentEntity entity);

    /**
     * 检查是否可修改 (仅 PENDING 状态可修改).
     */
    boolean isModifiable(BudgetAdjustmentEntity entity);
}