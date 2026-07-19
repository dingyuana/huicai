package com.huicai.sme.tax.service;

import java.math.BigDecimal;

/**
 * 进项发票状态机服务.
 * 与销项 OutputInvoiceStateMachineService 对称，但科目方向相反。
 * 详见 docs/specs/P40-input-invoice-state-machine.md
 */
public interface InputInvoiceStateMachineService {

    /** 提交审核 (PENDING_CONFIRM -> PENDING_REVIEW) */
    void submitForReview(Long invoiceId, Long userId);

    /** 审核通过 (PENDING_REVIEW -> CONFIRMED -> 自动创建 INVOICE_IN 业务单据 + 凭证 -> VOUCHERED) */
    void confirm(Long invoiceId, Long userId);

    /** 审核驳回 (PENDING_REVIEW -> PENDING_CONFIRM, 记录驳回原因) */
    void reject(Long invoiceId, Long userId, String reason);

    /** 回退到待审核 (CONFIRMED -> PENDING_REVIEW) */
    void revertToReview(Long invoiceId, Long userId);

    /** 标记已生成凭证 (CONFIRMED -> VOUCHERED, 记录 voucherId + voucherNo) */
    void markVouchered(Long invoiceId, Long voucherId, String voucherNo, Long userId);

    /** 核销扣减后更新状态 (VOUCHERED -> FULLY_RECONCILED / PARTIALLY_RECONCILED) */
    void onReconciliationUpdate(Long invoiceId, BigDecimal unsettledAmount, Long userId);

    /** 作废 (任意非终态 -> VOIDED, 记录作废原因) */
    void voidInvoice(Long invoiceId, Long userId, String reason);

    /** 红冲 (CONFIRMED/VOUCHERED/PARTIALLY_RECONCILED -> REVERSED, 生成红字进项发票) */
    Long reverseInvoice(Long invoiceId, Long userId, String reason);
}
