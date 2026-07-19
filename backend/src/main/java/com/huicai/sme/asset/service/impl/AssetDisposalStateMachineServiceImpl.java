package com.huicai.sme.asset.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.sme.asset.constant.AssetStatus;
import com.huicai.sme.asset.entity.AssetDisposalEntity;
import com.huicai.sme.asset.service.AssetDisposalStateMachineService;
import org.springframework.stereotype.Service;

/**
 * 资产处置状态机服务实现.
 */
@Service
public class AssetDisposalStateMachineServiceImpl implements AssetDisposalStateMachineService {

    @Override
    public void assertApprovable(AssetDisposalEntity entity) {
        if (!AssetStatus.isDisposalPending(entity.getStatus())) {
            throw BusinessException.badRequest(
                    "资产处置当前状态 " + entity.getStatus() + " 不可审核, 需 PENDING");
        }
    }

    @Override
    public void assertCompletable(AssetDisposalEntity entity) {
        if (!AssetStatus.isDisposalApproved(entity.getStatus())) {
            throw BusinessException.badRequest(
                    "资产处置当前状态 " + entity.getStatus() + " 不可完成, 需 APPROVED");
        }
    }

    @Override
    public boolean isApproved(AssetDisposalEntity entity) {
        return AssetStatus.isDisposalApproved(entity.getStatus());
    }

    @Override
    public boolean isCompleted(AssetDisposalEntity entity) {
        return AssetStatus.isDisposalCompleted(entity.getStatus());
    }

    @Override
    public boolean isModifiable(AssetDisposalEntity entity) {
        return AssetStatus.isDisposalPending(entity.getStatus());
    }
}