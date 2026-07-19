package com.huicai.sme.arap.service;

import com.huicai.common.exception.BusinessException;
import com.huicai.sme.arap.entity.BadDebtProvisionEntity;

/**
 * 坏账准备状态机服务.
 * 封装坏账准备 3 状态 (DRAFT/CONFIRMED/VOUCHERED) 的状态流转检查.
 */
public interface BadDebtProvisionStateMachineService {

    /**
     * 校验可确认 (DRAFT → CONFIRMED).
     *
     * @throws BusinessException 如果 status 不是 DRAFT
     */
    void assertConfirmable(BadDebtProvisionEntity entity);

    /**
     * 校验可生成凭证 (CONFIRMED → VOUCHERED).
     *
     * @throws BusinessException 如果 status 不是 CONFIRMED
     */
    void assertVoucherable(BadDebtProvisionEntity entity);

    /**
     * 检查是否为已确认状态.
     */
    boolean isConfirmed(BadDebtProvisionEntity entity);

    /**
     * 检查是否为已生成凭证状态.
     */
    boolean isVoucherable(BadDebtProvisionEntity entity);

    /**
     * 检查是否可修改 (仅 DRAFT 状态可修改).
     */
    boolean isModifiable(BadDebtProvisionEntity entity);
}