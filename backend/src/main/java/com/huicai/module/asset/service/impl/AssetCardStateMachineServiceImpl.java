package com.huicai.module.asset.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.asset.constant.AssetStatus;
import com.huicai.module.asset.entity.AssetCardEntity;
import com.huicai.module.asset.service.AssetCardStateMachineService;
import org.springframework.stereotype.Service;

/**
 * 资产卡片状态机服务实现.
 */
@Service
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