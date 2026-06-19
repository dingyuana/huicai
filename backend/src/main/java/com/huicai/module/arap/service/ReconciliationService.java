package com.huicai.module.arap.service;

import com.huicai.module.arap.entity.ReconciliationExceptionEntity;
import com.huicai.module.arap.entity.ReconciliationLogEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ReconciliationService {

    /** 核销推荐项 */
    record RecommendItem(
        Long targetDocId,
        String targetDocNo,
        String targetDocType,
        BigDecimal originalAmount,
        BigDecimal unsettledAmount,
        BigDecimal matchScore,
        String matchLevel,
        BigDecimal suggestedAmount
    ) {}

    /** 推荐结果 */
    record RecommendResult(
        String sourceDocType,
        Long sourceDocId,
        String counterpartyName,
        BigDecimal sourceAmount,
        List<RecommendItem> items
    ) {}

    /** 核销预检查单项结果 */
    record PreCheckItem(
        String checkName,
        boolean passed,
        String message
    ) {}

    /** 核销预检查结果 */
    record PreCheckResult(
        boolean allPassed,
        List<PreCheckItem> checks
    ) {}

    /** 执行核销请求 */
    record ExecuteRequest(
        String sourceDocType,
        Long sourceDocId,
        String targetDocType,
        Long targetDocId,
        BigDecimal amount,
        BigDecimal matchScore,
        String matchMethod,
        Long customerId,
        Long vendorId,
        String period,
        String remark
    ) {}

    /** 收款核销推荐 */
    RecommendResult recommendReceipt(Long receiptId, Long customerId, BigDecimal amount, String summary, String counterpartyName);

    /** 付款核销推荐 */
    RecommendResult recommendPayment(Long paymentId, Long vendorId, BigDecimal amount, String summary, String counterpartyName);

    /** 银行流水自动推荐 (L1-L5 级匹配) */
    RecommendResult recommendForStatement(Long statementId, Long accountId, String direction, BigDecimal amount, String counterpartyName, String summary, LocalDate txDate, String externalNo);

    /** 执行单笔核销 */
    ReconciliationLogEntity execute(ExecuteRequest request);

    /** 批量核销 */
    List<ReconciliationLogEntity> batchExecute(List<ExecuteRequest> requests);

    /** 查询核销记录 */
    List<ReconciliationLogEntity> getRecords(String sourceDocType, Long sourceDocId);

    /** 分页查询核销日志 */
    com.baomidou.mybatisplus.core.metadata.IPage<ReconciliationLogEntity> pageLogs(String sourceDocType, Integer current, Integer size);

    /** 反核销 (需原因) */
    void reverse(Long logId, String reason);

    /** 核销前预检查 (5项) */
    PreCheckResult preCheck(ExecuteRequest request);

    /** 审批执行核销 (CONFIRMED → EXECUTED) */
    ReconciliationLogEntity approve(Long logId);

    /** 驳回核销 (CONFIRMED → REJECTED, 恢复应收/应付未结金额) */
    void reject(Long logId, String reason);

    /** 带差额调整的核销 */
    ReconciliationLogEntity executeWithAdjustment(ExecuteRequest request, BigDecimal adjustAmount, String adjustType, Long adjustSubjectId);

    /** 预收/预付检测 — 判断客户/供应商是否已有未结清应收/应付 */
    boolean hasOpenInvoices(String targetDocType, Long partyId);

    // ==================== FIFO 自动核销策略 ====================

    /**
     * 按 FIFO 先进先出策略自动核销 — 按到期日(dueDate)升序，优先核销最早的未结清单据.
     *
     * @param partyId      客户ID(应收) 或 供应商ID(应付)
     * @param targetDocType INVOICE_OUT 或 INVOICE_IN
     * @param totalAmount  本次待核销总金额
     * @param sourceDocType 来源单据类型 (receipt/payment/bank_txn)
     * @param sourceDocId   来源单据 ID
     * @param period        会计期间 YYYYMM
     * @param summary       摘要
     * @return 核销日志列表
     */
    List<ReconciliationLogEntity> autoReconcileFifo(Long partyId, String targetDocType, BigDecimal totalAmount,
                                                     String sourceDocType, Long sourceDocId,
                                                     String period, String summary);

    /**
     * 按到期日范围获取未结清应收/应付 (FIFO排序).
     *
     * @param targetDocType INVOICE_OUT / INVOICE_IN
     * @param partyId       客户ID / 供应商ID
     * @param dueDateBefore 到期日 ≤ 此日期 (null=不过滤)
     * @return 按 dueDate/txDate 升序排列的未结清单据
     */
    List<?> getUnsettledInvoicesFifo(String targetDocType, Long partyId, LocalDate dueDateBefore);

    // ==================== 异常池管理 ====================

    ReconciliationExceptionEntity createException(
            String sourceDocType, Long sourceDocId,
            String targetDocType, Long targetDocId,
            Long partyId, String partyType,
            BigDecimal amount, BigDecimal unsettledAmount,
            String exceptionType, String exceptionReason,
            String matchSuggestion);

    com.baomidou.mybatisplus.core.metadata.IPage<ReconciliationExceptionEntity> pageExceptions(
            String status, String exceptionType, Integer current, Integer size);

    void resolveException(Long id, Long userId, String remark);

    void ignoreException(Long id, Long userId, String reason);

    ReconciliationLogEntity retryException(Long id, Long userId);

    // ==================== 多对多核销拓扑 ====================

    /**
     * 源拆分核销 — 一笔来源单据(N)按分配列表核销多笔目标(M).
     * 适用于: 一笔收款拆分核销多张发票、一笔付款拆分核销多张应付.
     *
     * @param sourceDocType 来源单据类型
     * @param sourceDocId   来源单据 ID
     * @param customerId    客户ID (应收核销)
     * @param vendorId      供应商ID (应付核销)
     * @param totalAmount   来源总金额
     * @param allocations   分配列表 (targetDocType/targetDocId/amount)
     * @param period        会计期间
     * @param summary       摘要
     * @return 核销日志列表
     */
    record AllocationItem(String targetDocType, Long targetDocId, BigDecimal amount) {}

    List<ReconciliationLogEntity> splitAllocate(
            String sourceDocType, Long sourceDocId,
            Long customerId, Long vendorId,
            BigDecimal totalAmount, List<AllocationItem> allocations,
            String period, String summary);

    /**
     * 智能最优匹配 — 自动计算 N:M 最优分配方案.
     * 将来源金额按 FIFO 顺序拆分到多张目标单据, 并自动处理剩余金额转入预收/预付.
     */
    List<ReconciliationLogEntity> smartAllocate(
            String sourceDocType, Long sourceDocId,
            Long partyId, String partyType,
            String targetDocType,
            BigDecimal totalAmount,
            String period, String summary);
}