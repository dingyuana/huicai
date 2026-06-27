package com.huicai.module.asset.service;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.asset.entity.AssetInventoryEntity;

/**
 * 资产盘点状态机服务.
 * 封装资产盘点 3 状态 (IN_PROGRESS/CONFIRMED/VOUCHERED) 的状态流转检查.
 */
public interface AssetInventoryStateMachineService {

    /**
     * 校验可确认差异 (IN_PROGRESS → CONFIRMED).
     *
     * @throws BusinessException 如果 status 不是 IN_PROGRESS
     */
    void assertConfirmable(AssetInventoryEntity entity);

    /**
     * 校验可生成凭证 (CONFIRMED → VOUCHERED).
     *
     * @throws BusinessException 如果 status 不是 CONFIRMED
     */
    void assertVoucherable(AssetInventoryEntity entity);

    /**
     * 检查是否为已确认状态.
     */
    boolean isConfirmed(AssetInventoryEntity entity);

    /**
     * 检查是否为已生成凭证状态.
     */
    boolean isVoucherable(AssetInventoryEntity entity);

    /**
     * 检查是否可修改 (仅 IN_PROGRESS 状态可修改).
     */
    boolean isModifiable(AssetInventoryEntity entity);
}