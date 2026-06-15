package com.huicai.module.arap.service;

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

    /** 反核销 */
    void reverse(Long logId);

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
}