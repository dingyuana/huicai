package com.huicai.module.finance.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.module.finance.entity.BankStatementEntity;

import java.util.List;
import java.util.Map;

public interface BankStatementService {
    IPage<BankStatementEntity> pageQuery(Long accountId, String status, Integer current, Integer size);
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
}
