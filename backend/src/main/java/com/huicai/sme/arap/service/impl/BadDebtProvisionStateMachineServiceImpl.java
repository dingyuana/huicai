package com.huicai.sme.arap.service.impl;

import org.springframework.transaction.annotation.Transactional;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.arap.constant.ArapStatus;
import com.huicai.sme.arap.entity.BadDebtProvisionEntity;
import com.huicai.sme.arap.service.BadDebtProvisionStateMachineService;
import org.springframework.stereotype.Service;

/**
 * 坏账准备状态机服务实现.
 */
@Service
/**
 * 类级事务：P102/M5b —— 走租户表的读写路径必须有事务，否则切面不触发、
 * app.enterprise_id 设不进去，非超级用户下 RLS 会把本企业数据也过滤掉（读 0 行）。
 */
@Transactional
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