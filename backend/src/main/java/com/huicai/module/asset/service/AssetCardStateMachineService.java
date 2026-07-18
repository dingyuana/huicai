package com.huicai.module.asset.service;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.asset.entity.AssetCardEntity;

/**
 * 资产卡片状态机服务.
 * 封装资产卡片 4 状态 (DRAFT/IN_USE/IDLE/DISPOSED) 的状态流转检查.
 */
public interface AssetCardStateMachineService {

    /**
     * 校验可启用 (DRAFT → IN_USE).
     *
     * @throws BusinessException 如果 status 不是 DRAFT
     */
    void assertActivable(AssetCardEntity entity);

    /**
     * 校验可停用 (IN_USE → IDLE).
     *
     * @throws BusinessException 如果 status 不是 IN_USE
     */
    void assertStoppable(AssetCardEntity entity);

    /**
     * 校验可重新启用 (IDLE → IN_USE).
     *
     * @throws BusinessException 如果 status 不是 IDLE
     */
    void assertRestartable(AssetCardEntity entity);

    /**
     * 校验可处置 (IN_USE/IDLE → DISPOSED).
     *
     * @throws BusinessException 如果 status 不是 IN_USE 或 IDLE
     */
    void assertDisposable(AssetCardEntity entity);

    /**
     * 检查是否为在用状态.
     */
    boolean isInUse(AssetCardEntity entity);

    /**
     * 检查是否为闲置状态.
     */
    boolean isStopped(AssetCardEntity entity);

    /**
     * 检查是否为已处置状态.
     */
    boolean isDisposed(AssetCardEntity entity);

    /**
     * 检查是否可修改 (仅 DRAFT 状态可修改).
     */
    boolean isModifiable(AssetCardEntity entity);
}