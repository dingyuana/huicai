package com.huicai.sme.asset.service.impl;

import org.springframework.transaction.annotation.Transactional;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.asset.constant.AssetStatus;
import com.huicai.sme.asset.entity.AssetCardEntity;
import com.huicai.sme.asset.service.AssetCardStateMachineService;
import org.springframework.stereotype.Service;

/**
 * 资产卡片状态机服务实现.
 */
@Service
/**
 * 类级事务：P102/M5b —— 走租户表的读写路径必须有事务，否则切面不触发、
 * app.enterprise_id 设不进去，非超级用户下 RLS 会把本企业数据也过滤掉（读 0 行）。
 */
@Transactional
public class AssetCardStateMachineServiceImpl implements AssetCardStateMachineService {

    @Override
    public void assertActivable(AssetCardEntity entity) {
        if (!AssetStatus.isAssetCardDraft(entity.getStatus())) {
            throw BusinessException.badRequest(
                    "资产卡片当前状态 " + entity.getStatus() + " 不可启用, 需 DRAFT");
        }
    }

    @Override
    public void assertStoppable(AssetCardEntity entity) {
        if (!AssetStatus.isAssetCardInUse(entity.getStatus())) {
            throw BusinessException.badRequest(
                    "资产卡片当前状态 " + entity.getStatus() + " 不可停用, 需 IN_USE");
        }
    }

    @Override
    public void assertRestartable(AssetCardEntity entity) {
        String status = entity.getStatus();
        if (!AssetStatus.isAssetCardIdle(status) && !AssetStatus.isAssetCardStopped(status)) {
            throw BusinessException.badRequest(
                    "资产卡片当前状态 " + status + " 不可重新启用, 需 IDLE");
        }
    }

    @Override
    public void assertDisposable(AssetCardEntity entity) {
        String status = entity.getStatus();
        if (!AssetStatus.isAssetCardInUse(status) && !AssetStatus.isAssetCardIdle(status) && !AssetStatus.isAssetCardStopped(status)) {
            throw BusinessException.badRequest(
                    "资产卡片当前状态 " + status + " 不可处置, 需 IN_USE 或 IDLE");
        }
    }

    @Override
    public boolean isInUse(AssetCardEntity entity) {
        return AssetStatus.isAssetCardInUse(entity.getStatus());
    }

    @Override
    public boolean isStopped(AssetCardEntity entity) {
        String status = entity.getStatus();
        return AssetStatus.isAssetCardIdle(status) || AssetStatus.isAssetCardStopped(status);
    }

    @Override
    public boolean isDisposed(AssetCardEntity entity) {
        return AssetStatus.isAssetCardDisposed(entity.getStatus());
    }

    @Override
    public boolean isModifiable(AssetCardEntity entity) {
        return AssetStatus.isAssetCardDraft(entity.getStatus());
    }
}