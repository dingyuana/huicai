package com.huicai.module.arap.service;

import com.huicai.module.arap.entity.ReconciliationLogEntity;

import java.math.BigDecimal;
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
        String matchLevel,   // GREEN / YELLOW
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

    /** 银行流水自动推荐 */
    RecommendResult recommendForStatement(Long statementId, Long accountId, String direction, BigDecimal amount, String counterpartyName, String summary);

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
}
