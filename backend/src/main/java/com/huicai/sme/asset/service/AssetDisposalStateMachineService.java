package com.huicai.sme.asset.service;

import com.huicai.common.exception.BusinessException;
import com.huicai.sme.asset.entity.AssetDisposalEntity;

/**
 * 资产处置状态机服务.
 * 封装资产处置 3 状态 (PENDING/APPROVED/COMPLETED) 的状态流转检查.
 */
public interface AssetDisposalStateMachineService {

    /**
     * 校验可审核通过 (PENDING → APPROVED).
     *
     * @throws BusinessException 如果 status 不是 PENDING
     */
    void assertApprovable(AssetDisposalEntity entity);

    /**
     * 校验可完成处置 (APPROVED → COMPLETED).
     *
     * @throws BusinessException 如果 status 不是 APPROVED
     */
    void assertCompletable(AssetDisposalEntity entity);

    /**
     * 检查是否为已审核状态.
     */
    boolean isApproved(AssetDisposalEntity entity);

    /**
     * 检查是否为已完成状态.
     */
    boolean isCompleted(AssetDisposalEntity entity);

    /**
     * 检查是否可修改 (仅 PENDING 状态可修改).
     */
    boolean isModifiable(AssetDisposalEntity entity);
}