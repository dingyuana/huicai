package com.huicai.sme.cash.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.sme.cash.entity.BankAccountEntity;
import com.huicai.sme.cash.entity.BankJournalEntity;
import com.huicai.base.business.entity.BankStatementEntity;
import com.huicai.sme.cash.mapper.BankAccountMapper;
import com.huicai.sme.cash.mapper.BankJournalMapper;
import com.huicai.base.business.mapper.BankStatementMapper;
import com.huicai.sme.cash.service.BankReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankReconciliationServiceImpl implements BankReconciliationService {

    private static final String LOCK_KEY_PREFIX = "bank:recon:lock:";
    private static final long DEFAULT_TTL_SECONDS = 300; // 5min

    // score thresholds
    private static final int AUTO_MATCH_THRESHOLD = 85;
    private static final int PENDING_CONFIRM_THRESHOLD = 60;

    private final BankAccountMapper accountMapper;
    private final BankJournalMapper journalMapper;
    private final BankStatementMapper statementMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    // ─── Existing ───

    @Override
    public Map<String, Object> generateAdjustment(Long accountId, String period) {
        BankAccountEntity account = accountMapper.selectById(accountId);
        if (account == null) throw new IllegalArgumentException("账户不存在");

        BigDecimal enterpriseBalance = journalMapper.sumAmountByAccount(accountId);

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

        // 企业已收银行未收 (unmatched journal INCOME)
        List<BankJournalEntity> unreconciledJournals = journalMapper.selectUnreconciled(accountId);
        BigDecimal enterpriseReceipts = BigDecimal.ZERO;
        BigDecimal enterprisePayments = BigDecimal.ZERO;
        for (BankJournalEntity j : unreconciledJournals) {
            if ("INCOME".equals(j.getTxType())) {
                enterpriseReceipts = enterpriseReceipts.add(j.getAmount());
            } else {
                enterprisePayments = enterprisePayments.add(j.getAmount());
            }
        }

        // 银行已收企业未收 / 银行已付企业未付 (unmatched statement)
        LambdaQueryWrapper<BankStatementEntity> unmatachedStmtWrapper = new LambdaQueryWrapper<>();
        unmatachedStmtWrapper.eq(BankStatementEntity::getAccountId, accountId)
                .in(BankStatementEntity::getMatchStatus, "UNMATCHED", "PENDING_CONFIRM");
        List<BankStatementEntity> unmatchedStmts = statementMapper.selectList(unmatachedStmtWrapper);
        BigDecimal bankReceipts = BigDecimal.ZERO;
        BigDecimal bankPayments = BigDecimal.ZERO;
        for (BankStatementEntity s : unmatchedStmts) {
            if ("INCOME".equals(s.getTxType()) || "TRANSFER_IN".equals(s.getTxType())) {
                bankReceipts = bankReceipts.add(s.getAmount());
            } else {
                bankPayments = bankPayments.add(s.getAmount());
            }
        }

        BigDecimal adjustedEnterpriseBalance = enterpriseBalance.add(enterpriseReceipts).subtract(enterprisePayments);
        BigDecimal adjustedBankBalance = bankBalance.add(bankReceipts).subtract(bankPayments);

        BigDecimal diff = adjustedEnterpriseBalance.subtract(adjustedBankBalance);

        Map<String, Object> result = new HashMap<>();
        result.put("accountId", accountId);
        result.put("accountName", account.getAccountName());
        result.put("accountNo", account.getAccountNo());
        result.put("period", period);
        result.put("enterpriseBalance", enterpriseBalance);
        result.put("bankBalance", bankBalance);
        result.put("enterpriseReceipts", enterpriseReceipts);
        result.put("enterprisePayments", enterprisePayments);
        result.put("bankReceipts", bankReceipts);
        result.put("bankPayments", bankPayments);
        result.put("adjustedEnterpriseBalance", adjustedEnterpriseBalance);
        result.put("adjustedBankBalance", adjustedBankBalance);
        result.put("diff", diff);
        result.put("balanced", diff.compareTo(BigDecimal.ZERO) == 0);
        return result;
    }

    @Override
    public Map<String, Object> summarize(Long accountId, String period) {
        List<BankJournalEntity> journals = journalMapper.selectList(
                new LambdaQueryWrapper<BankJournalEntity>()
                        .eq(BankJournalEntity::getAccountId, accountId));
        BigDecimal enterpriseCount = BigDecimal.valueOf(journals.size());

        long enterpriseReconciled = journals.stream().filter(j -> Boolean.TRUE.equals(j.getIsReconciled())).count();
        long enterpriseUnreconciled = enterpriseCount.longValue() - enterpriseReconciled;

        List<BankStatementEntity> stmts = statementMapper.selectList(
                new LambdaQueryWrapper<BankStatementEntity>()
                        .eq(BankStatementEntity::getAccountId, accountId));
        long stmtMatched = stmts.stream().filter(s -> "MATCHED".equals(s.getMatchStatus()) || "MANUAL_MATCHED".equals(s.getMatchStatus())).count();
        long stmtUnmatched = stmts.stream().filter(s -> "UNMATCHED".equals(s.getMatchStatus())).count();
        long stmtPending = stmts.stream().filter(s -> "PENDING_CONFIRM".equals(s.getMatchStatus())).count();
        long stmtIgnored = stmts.stream().filter(s -> "IGNORED".equals(s.getMatchStatus())).count();

        Map<String, Object> r = new HashMap<>();
        r.put("enterpriseTotal", enterpriseCount);
        r.put("enterpriseReconciled", enterpriseReconciled);
        r.put("enterpriseUnreconciled", enterpriseUnreconciled);
        r.put("statementTotal", stmts.size());
        r.put("statementMatched", stmtMatched);
        r.put("statementPendingConfirm", stmtPending);
        r.put("statementUnmatched", stmtUnmatched);
        r.put("statementIgnored", stmtIgnored);
        return r;
    }

    @Override
    public List<Map<String, Object>> unmatchedItems(Long accountId, String period) {
        List<Map<String, Object>> rows = new ArrayList<>();

        // 1. 银行已收企业未记: statement INCOME/TRANSFER_IN, UNMATCHED 或 PENDING_CONFIRM
        List<BankStatementEntity> stmtsReceipt = statementMapper.selectList(
                new LambdaQueryWrapper<BankStatementEntity>()
                        .eq(BankStatementEntity::getAccountId, accountId)
                        .in(BankStatementEntity::getMatchStatus, "UNMATCHED", "PENDING_CONFIRM")
                        .in(BankStatementEntity::getTxType, "INCOME", "TRANSFER_IN"));
        for (BankStatementEntity s : stmtsReceipt) {
            rows.add(buildUnmatchedRow("BANK_RECEIPT_ENTERPRISE_NOT", s.getId(), s.getTxDate(), s.getAmount(), s.getSummary(), s.getCounterAccount()));
        }

        // 2. 银行已付企业未记: statement EXPENSE/TRANSFER_OUT, UNMATCHED 或 PENDING_CONFIRM
        List<BankStatementEntity> stmtsPayment = statementMapper.selectList(
                new LambdaQueryWrapper<BankStatementEntity>()
                        .eq(BankStatementEntity::getAccountId, accountId)
                        .in(BankStatementEntity::getMatchStatus, "UNMATCHED", "PENDING_CONFIRM")
                        .in(BankStatementEntity::getTxType, "EXPENSE", "TRANSFER_OUT"));
        for (BankStatementEntity s : stmtsPayment) {
            rows.add(buildUnmatchedRow("BANK_PAYMENT_ENTERPRISE_NOT", s.getId(), s.getTxDate(), s.getAmount(), s.getSummary(), s.getCounterAccount()));
        }

        // 3. 企业已收银行未记: journal txType=INCOME, isReconciled=false
        List<BankJournalEntity> journalsReceipt = journalMapper.selectUnreconciled(accountId);
        for (BankJournalEntity j : journalsReceipt) {
            if ("INCOME".equals(j.getTxType())) {
                rows.add(buildUnmatchedRow("ENTERPRISE_RECEIPT_BANK_NOT", j.getId(), j.getTxDate(), j.getAmount(), j.getSummary(), j.getCounterAccount()));
            }
        }

        // 4. 企业已付银行未记: journal txType=EXPENSE/TRANSFER_OUT, isReconciled=false
        for (BankJournalEntity j : journalsReceipt) {
            if ("EXPENSE".equals(j.getTxType()) || "TRANSFER_OUT".equals(j.getTxType())) {
                rows.add(buildUnmatchedRow("ENTERPRISE_PAYMENT_BANK_NOT", j.getId(), j.getTxDate(), j.getAmount(), j.getSummary(), j.getCounterAccount()));
            }
        }

        return rows;
    }

    private Map<String, Object> buildUnmatchedRow(String type, Object id, LocalDate txDate, BigDecimal amount, String summary, String counterAccount) {
        Map<String, Object> row = new HashMap<>();
        row.put("type", type);
        row.put("id", id);
        row.put("txDate", txDate);
        row.put("amount", amount);
        row.put("summary", summary != null ? summary : "");
        row.put("counterAccount", counterAccount != null ? counterAccount : "");
        return row;
    }

    // ─── P4.1: 5维评分 ───

    @Override
    public ScoreResult calculateScore(Long accountId, Long statementId, Long journalId) {
        BankStatementEntity stmt = statementMapper.selectById(statementId);
        BankJournalEntity journal = journalMapper.selectById(journalId);
        if (stmt == null || journal == null) {
            return new ScoreResult(0, 0, 0, 0, 0, 0, "statement或journal不存在");
        }

        int amountScore = scoreAmount(stmt.getAmount(), journal.getAmount());
        int dateScore = scoreDate(stmt.getTxDate(), journal.getTxDate());
        int nameScore = scoreName(stmt.getCounterAccount(), journal.getCounterAccount());
        int descScore = scoreDescription(stmt.getSummary(), journal.getSummary());
        int refScore = scoreReference(stmt.getExternalNo(), journal.getBusinessDocId(), journal.getVoucherId());

        int total = amountScore + dateScore + nameScore + descScore + refScore;
        String remark = "金额" + amountScore + "+日期" + dateScore + "+名称" + nameScore
                + "+摘要" + descScore + "+参考号" + refScore + "=" + total;

        return new ScoreResult(total, amountScore, dateScore, nameScore, descScore, refScore, remark);
    }

    /**
     * 金额匹配 (50pt). 完全一致=50, 差异≤1%按比例衰减, >1%=0.
     */
    private int scoreAmount(BigDecimal stmtAmount, BigDecimal journalAmount) {
        if (stmtAmount == null || journalAmount == null) return 0;
        int cmp = stmtAmount.compareTo(journalAmount);
        if (cmp == 0) return 50;

        BigDecimal diff = stmtAmount.subtract(journalAmount).abs();
        BigDecimal max = stmtAmount.abs().max(journalAmount.abs());
        if (max.compareTo(BigDecimal.ZERO) == 0) return 0;

        BigDecimal ratio = diff.divide(max, 4, RoundingMode.HALF_UP);
        if (ratio.compareTo(new BigDecimal("0.01")) > 0) return 0;

        // 线性衰减: 50 * (1 - ratio/0.01)
        double score = 50.0 * (1.0 - ratio.doubleValue() / 0.01);
        return (int) Math.round(score);
    }

    /**
     * 日期匹配 (20pt). 同天=20, ±1天=15, ±2天=10, ±3天=5, >3天=0.
     */
    private int scoreDate(LocalDate stmtDate, LocalDate journalDate) {
        if (stmtDate == null || journalDate == null) return 0;
        long days = Math.abs(ChronoUnit.DAYS.between(stmtDate, journalDate));
        if (days == 0) return 20;
        if (days <= 1) return 15;
        if (days <= 2) return 10;
        if (days <= 3) return 5;
        return 0;
    }

    /**
     * 名称匹配 (15pt). Levenshtein ≥90%=15, ≥80%=10, ≥70%=5, <70=0.
     */
    private int scoreName(String stmtName, String journalName) {
        if (StrUtil.isBlank(stmtName) || StrUtil.isBlank(journalName)) return 0;
        double sim = levenshteinSimilarity(stmtName.toLowerCase(), journalName.toLowerCase());
        if (sim >= 0.90) return 15;
        if (sim >= 0.80) return 10;
        if (sim >= 0.70) return 5;
        return 0;
    }

    /**
     * 摘要匹配 (10pt). Jaccard 2-gram 相似度 * 10.
     */
    private int scoreDescription(String stmtSummary, String journalSummary) {
        if (StrUtil.isBlank(stmtSummary) || StrUtil.isBlank(journalSummary)) return 0;
        double sim = jaccardSimilarity(stmtSummary, journalSummary);
        return (int) Math.round(sim * 10);
    }

    /**
     * 参考号匹配 (5pt). externalNo 匹配 journal 的 businessDocId 或 voucherId 则为 5.
     */
    private int scoreReference(String externalNo, Long businessDocId, Long voucherId) {
        if (StrUtil.isBlank(externalNo)) return 0;
        if (businessDocId != null && externalNo.equals(String.valueOf(businessDocId))) return 5;
        if (voucherId != null && externalNo.equals(String.valueOf(voucherId))) return 5;
        return 0;
    }

    // ─── P4.2: 评分路由 ───

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<MatchResult> runMatching(Long accountId, String period) {
        List<BankStatementEntity> unmatchedStmts = statementMapper.selectList(
                new LambdaQueryWrapper<BankStatementEntity>()
                        .eq(BankStatementEntity::getAccountId, accountId)
                        .eq(BankStatementEntity::getMatchStatus, "UNMATCHED"));
        List<BankJournalEntity> unreconciledJournals = journalMapper.selectUnreconciled(accountId);

        List<MatchResult> results = new ArrayList<>();

        for (BankStatementEntity stmt : unmatchedStmts) {
            int bestScore = 0;
            BankJournalEntity bestJournal = null;

            for (BankJournalEntity journal : unreconciledJournals) {
                int s = scoreAmount(stmt.getAmount(), journal.getAmount())
                     + scoreDate(stmt.getTxDate(), journal.getTxDate())
                     + scoreName(stmt.getCounterAccount(), journal.getCounterAccount())
                     + scoreDescription(stmt.getSummary(), journal.getSummary())
                     + scoreReference(stmt.getExternalNo(), journal.getBusinessDocId(), journal.getVoucherId());
                if (s > bestScore) {
                    bestScore = s;
                    bestJournal = journal;
                }
            }

            if (bestJournal == null || bestScore < PENDING_CONFIRM_THRESHOLD) {
                results.add(new MatchResult(stmt.getId(), null, "UNMATCHED", bestScore, "未找到匹配日记账或分数不足"));
                continue;
            }

            if (bestScore >= AUTO_MATCH_THRESHOLD) {
                journalMapper.updateReconciled(bestJournal.getId(), true);
                statementMapper.updateMatch(stmt.getId(), bestJournal.getId(), "MATCHED");
                results.add(new MatchResult(stmt.getId(), bestJournal.getId(), "MATCHED", bestScore, "自动匹配成功"));
            } else {
                statementMapper.updateMatch(stmt.getId(), null, "PENDING_CONFIRM");
                results.add(new MatchResult(stmt.getId(), bestJournal.getId(), "PENDING_CONFIRM", bestScore,
                        "分数" + bestScore + "需人工确认, 最佳匹配日记账ID=" + bestJournal.getId()));
            }
        }

        return results;
    }

    // ─── P4.4: 对账锁定 ───

    @Override
    public boolean lockReconciliation(Long accountId, String period, String operator, long ttlSeconds) {
        String key = LOCK_KEY_PREFIX + accountId + ":" + period;
        long ttl = ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, operator, ttl, TimeUnit.SECONDS);
        boolean locked = Boolean.TRUE.equals(acquired);
        if (locked) {
            log.info("对账锁定成功: accountId={}, period={}, operator={}", accountId, period, operator);
        } else {
            String holder = (String) redisTemplate.opsForValue().get(key);
            log.warn("对账锁定失败, 当前持有者: accountId={}, period={}, holder={}", accountId, period, holder);
        }
        return locked;
    }

    @Override
    public void unlockReconciliation(Long accountId, String period, String operator) {
        String key = LOCK_KEY_PREFIX + accountId + ":" + period;
        String holder = (String) redisTemplate.opsForValue().get(key);
        if (operator.equals(holder)) {
            redisTemplate.delete(key);
            log.info("对账锁释放: accountId={}, period={}, operator={}", accountId, period, operator);
        } else {
            log.warn("无法释放对账锁: 操作者不匹配. accountId={}, period={}, operator={}, holder={}",
                    accountId, period, operator, holder);
        }
    }

    // ─── Similarity helpers (same algorithm as ReconciliationServiceImpl) ───

    private double levenshteinSimilarity(String a, String b) {
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 1.0;
        return 1.0 - (double) levenshteinDistance(a, b) / maxLen;
    }

    private int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            }
        }
        return dp[a.length()][b.length()];
    }

    private double jaccardSimilarity(String a, String b) {
        Set<String> gramsA = ngram2(a);
        Set<String> gramsB = ngram2(b);
        if (gramsA.isEmpty() && gramsB.isEmpty()) return 0;
        Set<String> intersection = new HashSet<>(gramsA);
        intersection.retainAll(gramsB);
        Set<String> union = new HashSet<>(gramsA);
        union.addAll(gramsB);
        return (double) intersection.size() / union.size();
    }

    private Set<String> ngram2(String text) {
        Set<String> grams = new HashSet<>();
        String clean = text.replaceAll("\\s+", "");
        for (int i = 0; i < clean.length() - 1; i++) {
            grams.add(clean.substring(i, i + 2));
        }
        return grams;
    }

    // ─── P14-1: 人工确认 / 驳回 ───

    @Override
    public ConfirmResult confirmMatch(Long statementId, Long journalId, String operator) {
        log.info("P14-1 确认匹配: statementId={}, journalId={}, operator={}",
                statementId, journalId, operator);
        // 实际生产: 更新 t_bank_statement.match_status = MATCHED, 记录 t_bank_reconciliation_log
        // 当前: 仅返回结果
        return new ConfirmResult(statementId, journalId, "MATCHED", operator);
    }

    @Override
    public ConfirmResult rejectMatch(Long statementId, Long journalId, String operator) {
        log.info("P14-1 驳回匹配: statementId={}, journalId={}, operator={}",
                statementId, journalId, operator);
        return new ConfirmResult(statementId, journalId, "UNMATCHED", operator);
    }
}
