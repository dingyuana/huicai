package com.huicai.module.finance.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface BankReconciliationService {

    // ─── Existing ───

    /** 余额调节表 */
    Map<String, Object> generateAdjustment(Long accountId, String period);
    /** 对账汇总 */
    Map<String, Object> summarize(Long accountId, String period);
    /** 未达账项 (4-方向分类) */
    List<Map<String, Object>> unmatchedItems(Long accountId, String period);

    // ─── P4.1: 5维评分 ───

    /** 单笔评分结果 */
    record ScoreResult(
        int totalScore,
        int amountScore,
        int dateScore,
        int nameScore,
        int descScore,
        int refScore,
        String remark
    ) {}

    /**
     * 对单笔银行流水与日记账执行 5 维评分.
     * @return ScoreResult 各维度得分及总分
     */
    ScoreResult calculateScore(
        Long accountId,
        Long statementId,
        Long journalId
    );

    // ─── P4.2: 评分路由 ───

    /** 单笔匹配结果 */
    record MatchResult(
        Long statementId,
        Long journalId,
        String matchStatus,   // MATCHED / PENDING_CONFIRM / UNMATCHED
        int score,
        String remark
    ) {}

    /**
     * 批量执行自动匹配 (所有未匹配 statement 按分数路由).
     * - ≥85: 自动 MATCHED
     * - 60-84: PENDING_CONFIRM (人工审核)
     * - <60: 保持 UNMATCHED (进入未达账项)
     * @return 匹配结果列表
     */
    List<MatchResult> runMatching(Long accountId, String period);

    // ─── P4.4: 对账锁定 ───

    /** 获取对账锁 (同一期间只允许一个用户操作) */
    boolean lockReconciliation(Long accountId, String period, String operator, long ttlSeconds);
    /** 释放对账锁 */
    void unlockReconciliation(Long accountId, String period, String operator);
}
