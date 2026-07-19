package com.huicai.sme.asset.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.sme.asset.constant.AssetStatus;
import com.huicai.sme.asset.entity.AssetInventoryEntity;
import com.huicai.sme.asset.service.AssetInventoryStateMachineService;
import org.springframework.stereotype.Service;

/**
 * 资产盘点状态机服务实现.
 */
@Service
public class AssetInventoryStateMachineServiceImpl implements AssetInventoryStateMachineService {

    @Override
    public void assertConfirmable(AssetInventoryEntity entity) {
        if (!AssetStatus.isInventoryInProgress(entity.getStatus())) {
            throw BusinessException.badRequest(
                    "资产盘点当前状态 " + entity.getStatus() + " 不可确认差异, 需 IN_PROGRESS");
        }
    }

    @Override
    public void assertVoucherable(AssetInventoryEntity entity) {
        if (!AssetStatus.isInventoryConfirmed(entity.getStatus())) {
            throw BusinessException.badRequest(
                    "资产盘点当前状态 " + entity.getStatus() + " 不可生成凭证, 需 CONFIRMED");
        }
    }

    @Override
    public boolean isConfirmed(AssetInventoryEntity entity) {
        return AssetStatus.isInventoryConfirmed(entity.getStatus());
    }

    @Override
    public boolean isVoucherable(AssetInventoryEntity entity) {
        return AssetStatus.isInventoryVouchered(entity.getStatus());
    }

    @Override
    public boolean isModifiable(AssetInventoryEntity entity) {
        return AssetStatus.isInventoryInProgress(entity.getStatus());
    }
}