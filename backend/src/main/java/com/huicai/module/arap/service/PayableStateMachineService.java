package com.huicai.module.arap.service;

import java.math.BigDecimal;

/**
 * 应付单状态机服务.
 * 详见 docs/specs/P20-arap-state-machine-spec.md
 */
public interface PayableStateMachineService {

    /**
     * 提交确认应付单 (DRAFT → CONFIRMED)
     * @param payableId 应付单ID
     * @param userId 操作人ID
     */
    void confirm(Long payableId, Long userId);

    /**
     * 核销更新应付单状态 (CONFIRMED → SETTLED, 当未结清金额为0时)
     * @param payableId 应付单ID
     * @param unsettledAmount 剩余未结清金额
     * @param userId 操作人ID
     */
    void onReconciliationUpdate(Long payableId, BigDecimal unsettledAmount, Long userId);

    /**
     * 冲销应付单 (CONFIRMED/SETTLED → REVERSED)
     * @param payableId 应付单ID
     * @param userId 操作人ID
     * @param reason 冲销原因
     */
    void reverse(Long payableId, Long userId, String reason);
}
