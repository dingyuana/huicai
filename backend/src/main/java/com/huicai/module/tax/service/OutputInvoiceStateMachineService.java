package com.huicai.module.tax.service;

import java.math.BigDecimal;

/**
 * 销售发票状态机服务.
 * 详见 docs/需求分析书_发票与凭证状态机_V1.0.md §3.1
 * SPEC: docs/specs/P21-sales-invoice-state-machine.md
 */
public interface OutputInvoiceStateMachineService {

    /** 提交审核 (PENDING_CONFIRM → PENDING_REVIEW) */
    void submitForReview(Long invoiceId, Long userId);

    /** 审核通过 (PENDING_REVIEW → CONFIRMED) */
    void confirm(Long invoiceId, Long userId);

    /** 审核驳回 (PENDING_REVIEW → PENDING_CONFIRM, 记录驳回原因) */
    void reject(Long invoiceId, Long userId, String reason);

    /** 回退到待审核 (CONFIRMED → PENDING_REVIEW, 选错结算状态) */
    void revertToReview(Long invoiceId, Long userId);

    /** 标记已生成凭证 (CONFIRMED → VOUCHERED, 记录 voucherId + voucherNo) */
    void markVouchered(Long invoiceId, Long voucherId, String voucherNo, Long userId);

    /** 核销扣减后更新状态 (VOUCHERED → FULLY_RECONCILED / PARTIALLY_RECONCILED) */
    void onReconciliationUpdate(Long invoiceId, BigDecimal unsettledAmount, Long userId);

    /** 作废 (任意非终态 → VOIDED, 记录作废原因) */
    void voidInvoice(Long invoiceId, Long userId, String reason);

    /** 红冲 (CONFIRMED/VOUCHERED/PARTIALLY_RECONCILED → 生成红字发票) */
    Long reverseInvoice(Long invoiceId, Long userId, String reason);
}