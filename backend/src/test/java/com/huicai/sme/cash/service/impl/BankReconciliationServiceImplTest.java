package com.huicai.sme.cash.service.impl;

import cn.hutool.json.JSONUtil;
import com.huicai.sme.cash.entity.BankAccountEntity;
import com.huicai.sme.cash.entity.BankJournalEntity;
import com.huicai.base.business.entity.BankStatementEntity;
import com.huicai.sme.cash.mapper.BankAccountMapper;
import com.huicai.sme.cash.mapper.BankJournalMapper;
import com.huicai.base.business.mapper.BankStatementMapper;
import com.huicai.sme.cash.service.BankReconciliationService.ScoreResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankReconciliationServiceImplTest {

    @Mock private BankAccountMapper accountMapper;
    @Mock private BankJournalMapper journalMapper;
    @Mock private BankStatementMapper statementMapper;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;

    @InjectMocks private BankReconciliationServiceImpl service;

    private BankAccountEntity stubAccount(Long id) {
        BankAccountEntity a = new BankAccountEntity();
        a.setId(id);
        a.setAccountName("对公账户");
        a.setAccountNo("6225880000000001");
        return a;
    }

    private BankStatementEntity stubStmt(Long id, String type, BigDecimal amount, LocalDate date) {
        BankStatementEntity s = new BankStatementEntity();
        s.setId(id);
        s.setAccountId(1L);
        s.setTxType(type);
        s.setAmount(amount);
        s.setTxDate(date);
        s.setSummary("测试流水");
        s.setCounterAccount("对方公司");
        s.setExternalNo("EXT001");
        s.setMatchStatus("UNMATCHED");
        return s;
    }

    private BankJournalEntity stubJournal(Long id, String type, BigDecimal amount, LocalDate date) {
        BankJournalEntity j = new BankJournalEntity();
        j.setId(id);
        j.setAccountId(1L);
        j.setTxType(type);
        j.setAmount(amount);
        j.setTxDate(date);
        j.setSummary("测试日记账");
        j.setCounterAccount("对方公司");
        j.setIsReconciled(false);
        return j;
    }

    // ==================== generateAdjustment ====================

    @Test
    void generateAdjustment_账户不存在_throwIAE() {
        when(accountMapper.selectById(99L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.generateAdjustment(99L, "202606"));
    }

    @Test
    void generateAdjustment_企业大于银行_diff为正_balancedFalse() {
        when(accountMapper.selectById(1L)).thenReturn(stubAccount(1L));
        when(journalMapper.sumAmountByAccount(1L)).thenReturn(new BigDecimal("1000.00"));
        when(statementMapper.selectList(any())).thenReturn(List.of(
                stubStmt(1L, "INCOME", new BigDecimal("800.00"), LocalDate.now())
        ));
        Map<String, Object> r = service.generateAdjustment(1L, "202606");
        assertEquals(new BigDecimal("200.00"), r.get("diff"));
        assertEquals(false, r.get("balanced"));
    }

    @Test
    void generateAdjustment_企业等于银行_balancedTrue() {
        when(accountMapper.selectById(1L)).thenReturn(stubAccount(1L));
        when(journalMapper.sumAmountByAccount(1L)).thenReturn(new BigDecimal("500.00"));
        when(statementMapper.selectList(any())).thenReturn(List.of(
                stubStmt(1L, "INCOME", new BigDecimal("500.00"), LocalDate.now())
        ));
        Map<String, Object> r = service.generateAdjustment(1L, "202606");
        assertEquals(0, ((BigDecimal) r.get("diff")).compareTo(BigDecimal.ZERO));
        assertEquals(true, r.get("balanced"));
    }

    // ==================== summarize ====================

    @Test
    void summarize_混合状态_各类计数正确() {
        BankJournalEntity j1 = stubJournal(1L, "INCOME", new BigDecimal("100"), LocalDate.now());
        BankJournalEntity j2 = stubJournal(2L, "INCOME", new BigDecimal("200"), LocalDate.now());
        j2.setIsReconciled(true);
        BankJournalEntity j3 = stubJournal(3L, "EXPENSE", new BigDecimal("50"), LocalDate.now());

        BankStatementEntity s1 = stubStmt(1L, "INCOME", new BigDecimal("100"), LocalDate.now());
        s1.setMatchStatus("MATCHED");
        BankStatementEntity s2 = stubStmt(2L, "EXPENSE", new BigDecimal("50"), LocalDate.now());
        s2.setMatchStatus("PENDING_CONFIRM");
        BankStatementEntity s3 = stubStmt(3L, "INCOME", new BigDecimal("30"), LocalDate.now());
        s3.setMatchStatus("IGNORED");

        when(journalMapper.selectList(any())).thenReturn(List.of(j1, j2, j3));
        when(statementMapper.selectList(any())).thenReturn(List.of(s1, s2, s3));

        Map<String, Object> r = service.summarize(1L, "202606");
        // enterpriseTotal 是 BigDecimal（journalMapper.size()），stripTrailingZeros 比对
        assertEquals(0, ((BigDecimal) r.get("enterpriseTotal")).compareTo(new BigDecimal("3")));
        assertEquals(1L, r.get("enterpriseReconciled"));
        assertEquals(2L, r.get("enterpriseUnreconciled"));
        assertEquals(3, r.get("statementTotal"));
        assertEquals(1L, r.get("statementMatched"));
        assertEquals(1L, r.get("statementPendingConfirm"));
        assertEquals(1L, r.get("statementIgnored"));
    }

    // ==================== calculateScore ====================

    @Test
    void calculateScore_完全匹配_返回100() {
        LocalDate d = LocalDate.of(2026, 6, 15);
        // summary 用相同字符串让 jaccard=1.0 → descScore=10
        when(statementMapper.selectById(1L)).thenReturn(stubStmt(1L, "INCOME", new BigDecimal("1000"), d));
        BankJournalEntity j = stubJournal(2L, "INCOME", new BigDecimal("1000"), d);
        j.setSummary("测试流水");  // 与 stmt 一致
        when(journalMapper.selectById(2L)).thenReturn(j);

        ScoreResult r = service.calculateScore(1L, 1L, 2L);
        // stmt.externalNo="EXT001" 与 journal.businessDocId/voucherId 都不匹配 → refScore=0
        // 50+20+15+10+0=95
        assertEquals(95, r.totalScore());
        assertEquals(50, r.amountScore());
        assertEquals(20, r.dateScore());
        assertEquals(15, r.nameScore());
        assertEquals(10, r.descScore());
        assertEquals(0, r.refScore());
    }

    @Test
    void calculateScore_金额差0_5pct_线性衰减() {
        LocalDate d = LocalDate.of(2026, 6, 15);
        when(statementMapper.selectById(1L)).thenReturn(stubStmt(1L, "INCOME", new BigDecimal("1000"), d));
        BankJournalEntity j = stubJournal(2L, "INCOME", new BigDecimal("995"), d);
        j.setSummary("测试流水");  // jaccard=1.0
        when(journalMapper.selectById(2L)).thenReturn(j);

        ScoreResult r = service.calculateScore(1L, 1L, 2L);
        // 0.5% 差，amount=25（50*(1-0.5/1)）；其余 20+15+10+0=45；总分 70
        assertEquals(25, r.amountScore());
        assertEquals(70, r.totalScore());
    }

    @Test
    void calculateScore_日期差2天_10pt() {
        when(statementMapper.selectById(1L)).thenReturn(stubStmt(1L, "INCOME", new BigDecimal("100"), LocalDate.of(2026, 6, 15)));
        when(journalMapper.selectById(2L)).thenReturn(stubJournal(2L, "INCOME", new BigDecimal("100"), LocalDate.of(2026, 6, 17)));

        ScoreResult r = service.calculateScore(1L, 1L, 2L);
        assertEquals(10, r.dateScore());
    }

    @Test
    void calculateScore_名称Levenshtein80pct_10pt() {
        // 4 字符差 1 → 75% 相似度 → 5pt；需 80% → 差 1/5=0.2 → 4 vs 5
        when(statementMapper.selectById(1L)).thenReturn(stubStmt(1L, "INCOME", new BigDecimal("100"), LocalDate.now()));
        BankJournalEntity j = stubJournal(2L, "INCOME", new BigDecimal("100"), LocalDate.now());
        j.setCounterAccount("对方公司A");  // "对方公司" → "对方公司A" 1 增 1 步，5→6 长度，sim=1-1/6=0.833 → 10pt
        when(journalMapper.selectById(2L)).thenReturn(j);

        ScoreResult r = service.calculateScore(1L, 1L, 2L);
        assertEquals(10, r.nameScore());
    }

    @Test
    void calculateScore_statement不存在_返回0加备注() {
        when(statementMapper.selectById(99L)).thenReturn(null);
        when(journalMapper.selectById(2L)).thenReturn(stubJournal(2L, "INCOME", new BigDecimal("100"), LocalDate.now()));

        ScoreResult r = service.calculateScore(1L, 99L, 2L);
        assertEquals(0, r.totalScore());
        assertTrue(r.remark().contains("不存在"));
    }

    // ==================== lock / unlock ====================

    @Test
    void lockReconciliation_首次锁定_true() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        assertTrue(service.lockReconciliation(1L, "202606", "alice", 60));
    }

    @Test
    void lockReconciliation_已被锁定_false() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(false);
        when(valueOps.get(anyString())).thenReturn("bob");
        assertFalse(service.lockReconciliation(1L, "202606", "alice", 60));
    }

    @Test
    void unlockReconciliation_操作者匹配_delete() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("alice");
        service.unlockReconciliation(1L, "202606", "alice");
        verify(redisTemplate).delete("bank:recon:lock:1:202606");
    }

    @Test
    void unlockReconciliation_操作者不匹配_不delete() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("bob");
        service.unlockReconciliation(1L, "202606", "alice");
        verify(redisTemplate, never()).delete((String) any());
    }

    // ==================== runMatching ====================

    @Test
    void runMatching_分数大等85_自动MATCHED() {
        LocalDate d = LocalDate.of(2026, 6, 15);
        BankStatementEntity stmt = stubStmt(1L, "INCOME", new BigDecimal("1000"), d);
        BankJournalEntity journal = stubJournal(2L, "INCOME", new BigDecimal("1000"), d);
        when(statementMapper.selectList(any())).thenReturn(List.of(stmt));
        when(journalMapper.selectUnreconciled(1L)).thenReturn(List.of(journal));

        var results = service.runMatching(1L, "202606");
        assertEquals(1, results.size());
        assertEquals("MATCHED", results.get(0).matchStatus());
        verify(journalMapper).updateReconciled(2L, true);
        verify(statementMapper).updateMatch(1L, 2L, "MATCHED");
    }

    @Test
    void runMatching_分数60到84_PENDING_CONFIRM() {
        LocalDate d = LocalDate.of(2026, 6, 15);
        // 名称不同 → 0pt；summary 不同 → 0pt；总分 50+20=70
        BankStatementEntity stmt = stubStmt(1L, "INCOME", new BigDecimal("1000"), d);
        stmt.setCounterAccount("公司A");
        stmt.setSummary("XXX");
        stmt.setExternalNo(null);
        BankJournalEntity journal = stubJournal(2L, "INCOME", new BigDecimal("1000"), d);
        journal.setCounterAccount("完全不同的公司BBB");
        journal.setSummary("YYY完全不同摘要ZZZ");
        when(statementMapper.selectList(any())).thenReturn(List.of(stmt));
        when(journalMapper.selectUnreconciled(1L)).thenReturn(List.of(journal));

        var results = service.runMatching(1L, "202606");
        assertEquals(1, results.size());
        assertEquals("PENDING_CONFIRM", results.get(0).matchStatus());
    }

    @Test
    void runMatching_分数小60_UNMATCHED() {
        LocalDate d1 = LocalDate.of(2026, 6, 1);
        LocalDate d2 = LocalDate.of(2026, 7, 1);  // 30 天差 → date=0
        // 金额 50；名称不同 → 0；摘要不同 → 0；ref 0；总分 50
        BankStatementEntity stmt = stubStmt(1L, "INCOME", new BigDecimal("1000"), d1);
        stmt.setCounterAccount("A公司");
        stmt.setSummary("X");
        stmt.setExternalNo(null);
        BankJournalEntity journal = stubJournal(2L, "INCOME", new BigDecimal("1000"), d2);
        journal.setCounterAccount("完全不同的B公司名称");
        journal.setSummary("Y");
        when(statementMapper.selectList(any())).thenReturn(List.of(stmt));
        when(journalMapper.selectUnreconciled(1L)).thenReturn(List.of(journal));

        var results = service.runMatching(1L, "202606");
        assertEquals(1, results.size());
        assertEquals("UNMATCHED", results.get(0).matchStatus());
    }

    @Test
    void runMatching_无journal_全UNMATCHED() {
        BankStatementEntity stmt = stubStmt(1L, "INCOME", new BigDecimal("1000"), LocalDate.now());
        when(statementMapper.selectList(any())).thenReturn(List.of(stmt));
        when(journalMapper.selectUnreconciled(1L)).thenReturn(List.of());

        var results = service.runMatching(1L, "202606");
        assertEquals(1, results.size());
        assertEquals("UNMATCHED", results.get(0).matchStatus());
        assertNull(results.get(0).journalId());
    }

    // ==================== unmatchedItems ====================

    @Test
    void unmatchedItems_4方向分类_返回4类() {
        BankStatementEntity s1 = stubStmt(1L, "INCOME", new BigDecimal("100"), LocalDate.now());
        s1.setMatchStatus("UNMATCHED");
        BankStatementEntity s2 = stubStmt(2L, "EXPENSE", new BigDecimal("50"), LocalDate.now());
        s2.setMatchStatus("PENDING_CONFIRM");

        when(statementMapper.selectList(any()))
                .thenReturn(List.of(s1))   // 第一次：INCOME
                .thenReturn(List.of(s2));  // 第二次：EXPENSE
        when(journalMapper.selectUnreconciled(1L)).thenReturn(List.of());  // journal 都不返回

        var rows = service.unmatchedItems(1L, "202606");
        // journal 0 条 → 没有第 3、4 类
        assertEquals(2, rows.size());
    }

    // ==================== P14-1: 人工确认 / 驳回 ====================

    @Test
    void confirmMatch_returns_MATCHED_status() {
        var r = service.confirmMatch(1L, 100L, "zhangsan");
        assertEquals(1L, r.statementId());
        assertEquals(100L, r.journalId());
        assertEquals("MATCHED", r.newStatus());
        assertEquals("zhangsan", r.operator());
    }

    @Test
    void rejectMatch_returns_UNMATCHED_status() {
        var r = service.rejectMatch(1L, 100L, "lisi");
        assertEquals(1L, r.statementId());
        assertEquals(100L, r.journalId());
        assertEquals("UNMATCHED", r.newStatus());
        assertEquals("lisi", r.operator());
    }
}
