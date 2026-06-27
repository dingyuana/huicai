package com.huicai.module.arap.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.constant.ArapStatus;
import com.huicai.module.arap.entity.BadDebtProvisionEntity;
import com.huicai.module.arap.service.BadDebtProvisionStateMachineService;
import org.springframework.stereotype.Service;

/**
 * 坏账准备状态机服务实现.
 */
@Service
public class BadDebtProvisionStateMachineServiceImpl implements BadDebtProvisionStateMachineService {

    @Override
    public void assertConfirmable(BadDebtProvisionEntity entity) {
        if (!ArapStatus.isDraft(entity.getStatus())) {
            throw BusinessException.badRequest(
                    "坏账准备当前状态 " + entity.getStatus() + " 不可确认, 需 DRAFT");
        }
    }

    @Override
    public void assertVoucherable(BadDebtProvisionEntity entity) {
        if (!ArapStatus.isConfirmed(entity.getStatus())) {
            throw BusinessException.badRequest(
                    "坏账准备当前状态 " + entity.getStatus() + " 不可生成凭证, 需 CONFIRMED");
        }
    }

    @Override
    public boolean isConfirmed(BadDebtProvisionEntity entity) {
        return ArapStatus.isConfirmed(entity.getStatus());
    }

    @Override
    public boolean isVoucherable(BadDebtProvisionEntity entity) {
        return ArapStatus.VOUCHERED.equals(entity.getStatus());
    }

    @Override
    public boolean isModifiable(BadDebtProvisionEntity entity) {
        return ArapStatus.isModifiable(entity.getStatus());
    }
}