package com.huicai.module.finance.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.module.finance.entity.BankStatementEntity;

import java.util.List;
import java.util.Map;

public interface BankStatementService {
    IPage<BankStatementEntity> pageQuery(Long accountId, String status, String classification, String reviewStatus, Integer current, Integer size);
    int importFromCsv(Long accountId, String csvContent);
    List<Map<String, Object>> autoMatch(Long accountId);
    int confirmMatch(Long statementId, Long journalId);
    int ignoreStatement(Long statementId);
    List<BankStatementEntity> listUnmatched(Long accountId);

    /**
     * 对单条对账单执行规则分类
     */
    BankStatementEntity classifySingle(Long statementId);

    /**
     * 出纳单条确认分类. 更新 reviewStatus=CONFIRMED, reviewedBy, reviewedAt.
     * 验收第 9 条: 不在导入时自动创建业务单据, 确认后才触发
     */
    BankStatementEntity review(Long statementId);

    /**
     * 批量确认. salary_payment 分类时记录业务单据生成意图 (DRAFT, FROM_BANK_TXN).
     * 验收第 10 条: salary_payment 出纳确认时生成付款单 (DRAFT 状态, 关联员工档案)
     */
    int batchReview(List<Long> statementIds);

    /**
     * 核准过账. 仅允许 voucher_generated / payment_created 状态推进到 approved.
     */
    void approve(Long statementId);

    /**
     * P2: C类人工指定 A/B 类型后处理.
     * @param statementId 流水 ID
     * @param targetType  指定类型 A/B
     * @param paymentType B类时指定收支方向 pay/receive
     */
    BankStatementEntity processManual(Long statementId, String targetType, String paymentType);

    /**
     * P2: 预览凭证草稿 — 只计算不写入, 返回凭证分录 JSON.
     */
    List<PreviewEntry> previewDraft(Long statementId);

    /** P2: 预览分录 DTO */
    record PreviewEntry(
        String direction,      // debit/credit
        String subjectCode,    // 科目编码
        String subjectName,    // 科目名称
        java.math.BigDecimal amount,
        String summary
    ) {}

    /**
     * 获取单条对账单详情
     */
    BankStatementEntity getDetail(Long id);

    /**
     * 删除单条对账单(逻辑删除)
     */
    void deleteStatement(Long id);

    /**
     * 手动修改流水分类
     */
    BankStatementEntity updateClassification(Long id, String classification);

    /**
     * 按 accountId + 可选 reviewStatus 统计各分类的流水数量.
     * 返回结构: { classification: count }, 未分类 (NULL classification) 归入 "pending" 键.
     */
    Map<String, Integer> classificationCounts(Long accountId, String reviewStatus);

    /**
     * P5.2: 获取核销推荐. 对 business_receipt/business_payment 分类的流水,
     * 调用 ReconciliationService.recommendForStatement 匹配应收/应付.
     */
    ReconciliationRecommendResult reconciliationRecommend(Long statementId);

    /** P5.2: 核销推荐结果 DTO */
    record ReconciliationRecommendResult(
        String message,
        List<com.huicai.module.arap.service.ReconciliationService.RecommendItem> items
    ) {}
}
