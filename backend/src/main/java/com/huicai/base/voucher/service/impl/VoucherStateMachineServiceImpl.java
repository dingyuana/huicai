package com.huicai.base.voucher.service.impl;

import org.springframework.transaction.annotation.Transactional;
import com.huicai.common.exception.BusinessException;
import com.huicai.base.voucher.constant.VoucherStatus;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.service.VoucherStateMachineService;
import org.springframework.stereotype.Service;

/**
 * VoucherStateMachineService 实现.
 * 2026-06-22 P22 创建
 */
@Service
/**
 * 类级事务：P102/M5b —— 走租户表的读写路径必须有事务，否则切面不触发、
 * app.enterprise_id 设不进去，非超级用户下 RLS 会把本企业数据也过滤掉（读 0 行）。
 */
@Transactional
public class VoucherStateMachineServiceImpl implements VoucherStateMachineService {

    @Override
    public void assertSubmittable(VoucherEntity entity) {
        if (!VoucherStatus.isSubmittable(entity.getStatus())) {
            throw BusinessException.badRequest(
                    "凭证当前状态 " + entity.getStatus() + " 不可提交, 需 DRAFT");
        }
    }

    @Override
    public void assertAuditable(VoucherEntity entity) {
        if (!VoucherStatus.isAuditable(entity.getStatus())) {
            throw BusinessException.badRequest(
                    "凭证当前状态 " + entity.getStatus() + " 不可审核, 需 SUBMITTED");
        }
    }

    @Override
    public void assertPostable(VoucherEntity entity) {
        if (!VoucherStatus.isPostable(entity.getStatus())) {
            throw BusinessException.badRequest(
                    "凭证当前状态 " + entity.getStatus() + " 不可记账, 需 AUDITED");
        }
    }

    @Override
    public void assertReversible(VoucherEntity entity) {
        if (!VoucherStatus.isReversible(entity.getStatus())) {
            throw BusinessException.badRequest(
                    "凭证当前状态 " + entity.getStatus() + " 不可红冲, 需 POSTED 或 CLOSED");
        }
    }

    @Override
    public void assertClosable(VoucherEntity entity) {
        if (!VoucherStatus.isClosable(entity.getStatus())) {
            throw BusinessException.badRequest(
                    "凭证当前状态 " + entity.getStatus() + " 不可结账, 需 POSTED");
        }
    }
}
