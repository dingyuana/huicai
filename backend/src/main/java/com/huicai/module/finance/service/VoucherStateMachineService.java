package com.huicai.module.finance.service;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.finance.constant.VoucherStatus;
import com.huicai.module.finance.entity.VoucherEntity;

/**
 * 凭证状态机服务.
 * 封装凭证 4 状态 + 2 附属状态(REJECTED/REVERSED) 的状态流转检查.
 * 依据: docs/specs/P22-voucher-state-machine.md (2026-06-22 修订版)
 * 2026-06-22 P22 创建
 *
 * <p>本服务只做状态检查 + 状态字段读取, 不写库. 写库由 VoucherService 调用 voucherMapper.</p>
 */
public interface VoucherStateMachineService {

    /**
     * 校验可提交 (DRAFT → SUBMITTED).
     *
     * @throws BusinessException 如果 status 不是 DRAFT
     */
    void assertSubmittable(VoucherEntity entity);

    /**
     * 校验可审核 (SUBMITTED → AUDITED).
     */
    void assertAuditable(VoucherEntity entity);

    /**
     * 校验可记账 (AUDITED → POSTED).
     */
    void assertPostable(VoucherEntity entity);

    /**
     * 校验可红冲 (POSTED → 红字凭证, reversedFrom + reverseReason).
     */
    void assertReversible(VoucherEntity entity);

    /**
     * 检查凭证是否被红冲 (status=POSTED + reversedFrom 非空).
     */
    default boolean isReversed(VoucherEntity entity) {
        return VoucherStatus.isReversed(entity.getReversedFrom());
    }
}
