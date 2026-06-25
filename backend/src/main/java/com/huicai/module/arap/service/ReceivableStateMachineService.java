package com.huicai.module.arap.service;

import java.math.BigDecimal;

/**
 * 应收单状态机服务.
 * 详见 docs/specs/P20-arap-state-machine-spec.md
 */
public interface ReceivableStateMachineService {

    /**
     * 提交确认应收单 (DRAFT → CONFIRMED)
     * @param receivableId 应收单ID
     * @param userId 操作人ID
     */
    void confirm(Long receivableId, Long userId);

    /**
     * 核销更新应收单状态 (CONFIRMED → SETTLED, 当未结清金额为0时)
     * @param receivableId 应收单ID
     * @param unsettledAmount 剩余未结清金额
     * @param userId 操作人ID
     */
    void onReconciliationUpdate(Long receivableId, BigDecimal unsettledAmount, Long userId);

    /**
     * 冲销应收单 (CONFIRMED/SETTLED → REVERSED)
     * @param receivableId 应收单ID
     * @param userId 操作人ID
     * @param reason 冲销原因
     */
    void reverse(Long receivableId, Long userId, String reason);
}
