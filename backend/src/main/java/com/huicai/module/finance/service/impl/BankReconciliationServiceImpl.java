package com.huicai.module.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.module.finance.entity.BankAccountEntity;
import com.huicai.module.finance.entity.BankJournalEntity;
import com.huicai.module.finance.entity.BankStatementEntity;
import com.huicai.module.finance.mapper.BankAccountMapper;
import com.huicai.module.finance.mapper.BankJournalMapper;
import com.huicai.module.finance.mapper.BankStatementMapper;
import com.huicai.module.finance.service.BankReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BankReconciliationServiceImpl implements BankReconciliationService {

    private final BankAccountMapper accountMapper;
    private final BankJournalMapper journalMapper;
    private final BankStatementMapper statementMapper;

    @Override
    public Map<String, Object> generateAdjustment(Long accountId, String period) {
        BankAccountEntity account = accountMapper.selectById(accountId);
        if (account == null) throw new IllegalArgumentException("账户不存在");

        // 企业日记账余额: sum(income+transfer_in) - sum(expense+transfer_out)
        BigDecimal enterpriseBalance = journalMapper.sumAmountByAccount(accountId);

        // 银行对账单余额: 所有导入的银行账 (无论是否对账, 用于对账)
        LambdaQueryWrapper<BankStatementEntity> stmtWrapper = new LambdaQueryWrapper<>();
        stmtWrapper.eq(BankStatementEntity::getAccountId, accountId)
                .ne(BankStatementEntity::getMatchStatus, "IGNORED");
        List<BankStatementEntity> stmts = statementMapper.selectList(stmtWrapper);
        BigDecimal bankBalance = BigDecimal.ZERO;
        for (BankStatementEntity s : stmts) {
            if ("INCOME".equals(s.getTxType()) || "TRANSFER_IN".equals(s.getTxType())) {
                bankBalance = bankBalance.add(s.getAmount());
            } else {
                bankBalance = bankBalance.subtract(s.getAmount());
            }
        }

        BigDecimal diff = enterpriseBalance.subtract(bankBalance);

        Map<String, Object> result = new HashMap<>();
        result.put("accountId", accountId);
        result.put("accountName", account.getAccountName());
        result.put("accountNo", account.getAccountNo());
        result.put("period", period);
        result.put("enterpriseBalance", enterpriseBalance);
        result.put("bankBalance", bankBalance);
        result.put("diff", diff);
        result.put("balanced", diff.compareTo(BigDecimal.ZERO) == 0);
        return result;
    }

    @Override
    public Map<String, Object> summarize(Long accountId, String period) {
        // 企业已记
        List<BankJournalEntity> journals = journalMapper.selectList(
                new LambdaQueryWrapper<BankJournalEntity>()
                        .eq(BankJournalEntity::getAccountId, accountId));
        BigDecimal enterpriseCount = BigDecimal.valueOf(journals.size());

        long enterpriseReconciled = journals.stream().filter(j -> Boolean.TRUE.equals(j.getIsReconciled())).count();
        long enterpriseUnreconciled = enterpriseCount.longValue() - enterpriseReconciled;

        // 银行账
        List<BankStatementEntity> stmts = statementMapper.selectList(
                new LambdaQueryWrapper<BankStatementEntity>()
                        .eq(BankStatementEntity::getAccountId, accountId));
        long stmtMatched = stmts.stream().filter(s -> "MATCHED".equals(s.getMatchStatus()) || "MANUAL_MATCHED".equals(s.getMatchStatus())).count();
        long stmtUnmatched = stmts.stream().filter(s -> "UNMATCHED".equals(s.getMatchStatus())).count();
        long stmtIgnored = stmts.stream().filter(s -> "IGNORED".equals(s.getMatchStatus())).count();

        Map<String, Object> r = new HashMap<>();
        r.put("enterpriseTotal", enterpriseCount);
        r.put("enterpriseReconciled", enterpriseReconciled);
        r.put("enterpriseUnreconciled", enterpriseUnreconciled);
        r.put("statementTotal", stmts.size());
        r.put("statementMatched", stmtMatched);
        r.put("statementUnmatched", stmtUnmatched);
        r.put("statementIgnored", stmtIgnored);
        return r;
    }

    @Override
    public List<Map<String, Object>> unmatchedItems(Long accountId, String period) {
        List<Map<String, Object>> rows = new ArrayList<>();

        // 企业已记但银行未对账 (企业未达)
        List<BankJournalEntity> journals = journalMapper.selectList(
                new LambdaQueryWrapper<BankJournalEntity>()
                        .eq(BankJournalEntity::getAccountId, accountId)
                        .eq(BankJournalEntity::getIsReconciled, false));
        for (BankJournalEntity j : journals) {
            Map<String, Object> row = new HashMap<>();
            row.put("type", "ENTERPRISE_ONLY");
            row.put("id", j.getId());
            row.put("txDate", j.getTxDate());
            row.put("amount", j.getAmount());
            row.put("summary", j.getSummary());
            row.put("counterAccount", j.getCounterAccount());
            rows.add(row);
        }

        // 银行有企业无 (银行未达)
        List<BankStatementEntity> stmts = statementMapper.selectList(
                new LambdaQueryWrapper<BankStatementEntity>()
                        .eq(BankStatementEntity::getAccountId, accountId)
                        .eq(BankStatementEntity::getMatchStatus, "UNMATCHED"));
        for (BankStatementEntity s : stmts) {
            Map<String, Object> row = new HashMap<>();
            row.put("type", "BANK_ONLY");
            row.put("id", s.getId());
            row.put("txDate", s.getTxDate());
            row.put("amount", s.getAmount());
            row.put("summary", s.getSummary());
            row.put("counterAccount", s.getCounterAccount());
            rows.add(row);
        }
        return rows;
    }
}
