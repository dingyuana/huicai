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
}
