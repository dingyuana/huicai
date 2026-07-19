package com.huicai.sme.budget.service;

import com.huicai.common.exception.BusinessException;
import com.huicai.sme.budget.entity.BudgetEntity;

/**
 * 预算状态机服务.
 * 封装预算 4 状态 (DRAFT/SUBMITTED/APPROVED/FROZEN) 的状态流转检查.
 */
public interface BudgetStateMachineService {

    /**
     * 校验可提交 (DRAFT → SUBMITTED).
     *
     * @throws BusinessException 如果 status 不是 DRAFT
     */
    void assertSubmittable(BudgetEntity entity);

    /**
     * 校验可批准 (SUBMITTED → APPROVED).
     *
     * @throws BusinessException 如果 status 不是 SUBMITTED
     */
    void assertApprovable(BudgetEntity entity);

    /**
     * 校验可冻结 (APPROVED → FROZEN).
     *
     * @throws BusinessException 如果 status 不是 APPROVED
     */
    void assertFreezable(BudgetEntity entity);

    /**
     * 检查是否为已批准状态.
     */
    boolean isApproved(BudgetEntity entity);

    /**
     * 检查是否为已冻结状态.
     */
    boolean isFrozen(BudgetEntity entity);

    /**
     * 检查是否可修改 (仅 DRAFT 状态可修改).
     */
    boolean isModifiable(BudgetEntity entity);
}