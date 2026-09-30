package com.huicai.sme.cash.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.business.entity.BankStatementEntity;
import com.huicai.base.business.mapper.BankStatementMapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.common.test.SlowTest;
import com.huicai.sme.cash.entity.BankJournalEntity;
import com.huicai.sme.cash.entity.BankReconciliationLogEntity;
import com.huicai.sme.cash.mapper.BankJournalMapper;
import com.huicai.sme.cash.mapper.BankReconciliationLogMapper;
import com.huicai.sme.cash.service.BankReconciliationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D4 银行对账人工确认/驳回落库（REQ-2026-134 / P107）。
 *
 * <p>缺陷：confirmMatch/rejectMatch 仅 log.info 后返回 ConfirmResult，
 * 既不写 {@code t_bank_statement.match_status}，也不动
 * {@code t_bank_journal.is_reconciled}，更无对账日志 —— 人工确认结果凭空丢失。
 *
 * <p>修法：复用 runMatching 的既有落库范式（statementMapper.updateMatch +
 * journalMapper.updateReconciled），人工确认落 {@code MANUAL_MATCHED}
 * （与自动匹配的 MATCHED 区分），并写 t_bank_reconciliation_log 审计日志。
 *
 * <p>NOT_SUPPORTED 不回滚，故按 id 精确清理本类造的数据。
 */
@SlowTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BankReconciliationConfirmRealDBTest extends AbstractMapperTest {

    @Autowired private BankReconciliationService reconService;
    @Autowired private BankStatementMapper statementMapper;
    @Autowired private BankJournalMapper journalMapper;
    @Autowired private BankReconciliationLogMapper reconLogMapper;

    private final List<Long> createdStatementIds = new java.util.ArrayList<>();
    private final List<Long> createdJournalIds = new java.util.ArrayList<>();
    private final List<Long> createdLogIds = new java.util.ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long id : createdLogIds) {
            reconLogMapper.deleteById(id);
        }
        for (Long id : createdStatementIds) {
            statementMapper.deleteById(id);
        }
        for (Long id : createdJournalIds) {
            journalMapper.deleteById(id);
        }
        createdLogIds.clear();
        createdStatementIds.clear();
        createdJournalIds.clear();
    }

    private BankStatementEntity newStatement(String matchStatus) {
        BankStatementEntity s = new BankStatementEntity();
        s.setAccountId(ensureBankAccount(1L));
        s.setTxDate(LocalDate.of(2026, 8, 1));
        s.setTxType("INCOME");
        s.setAmount(new BigDecimal("1000.00"));
        s.setCounterAccount("客户A");
        s.setSummary("D4 测试货款");
        s.setMatchStatus(matchStatus);
        s.setEnterpriseId(1L);
        statementMapper.insert(s);
        createdStatementIds.add(s.getId());
        return s;
    }

    private BankJournalEntity newJournal(boolean reconciled) {
        BankJournalEntity j = new BankJournalEntity();
        j.setAccountId(ensureBankAccount(1L));
        j.setTxDate(LocalDate.of(2026, 8, 1));
        j.setPeriod("202608");  // 列宽 6，格式 yyyyMM
        j.setTxType("INCOME");
        j.setAmount(new BigDecimal("1000.00"));
        j.setCounterAccount("客户A");
        j.setSummary("D4 测试货款");
        j.setIsReconciled(reconciled);
        j.setEnterpriseId(1L);
        journalMapper.insert(j);
        createdJournalIds.add(j.getId());
        return j;
    }

    // ===== 正向 =====

    @Test
    void confirmMatch_应落库MANUAL_MATCHED并标记日记账已对账() {
        BankStatementEntity stmt = newStatement("UNMATCHED");
        BankJournalEntity journal = newJournal(false);

        BankReconciliationService.ConfirmResult result =
                reconService.confirmMatch(stmt.getId(), journal.getId(), "tester01");

        assertEquals("MANUAL_MATCHED", result.newStatus(), "返回值应与落库状态一致");

        BankStatementEntity reloaded = statementMapper.selectById(stmt.getId());
        assertEquals("MANUAL_MATCHED", reloaded.getMatchStatus(),
                "流水 match_status 必须落库（人工确认用 MANUAL_MATCHED，与自动 MATCHED 区分）");
        assertEquals(journal.getId(), reloaded.getMatchedJournalId(),
                "必须回写关联日记账 id，否则同一日记账可被重复匹配");

        BankJournalEntity reloadedJournal = journalMapper.selectById(journal.getId());
        assertTrue(Boolean.TRUE.equals(reloadedJournal.getIsReconciled()),
                "日记账必须标记为已对账");

        BankReconciliationLogEntity log = reconLogMapper.selectOne(
                new LambdaQueryWrapper<BankReconciliationLogEntity>()
                        .eq(BankReconciliationLogEntity::getStatementId, stmt.getId())
                        .last("LIMIT 1"));
        assertNotNull(log, "人工确认必须留审计日志（铁律 #5）");
        createdLogIds.add(log.getId());
        assertEquals("CONFIRM", log.getAction());
        assertEquals("tester01", log.getOperator());
        assertEquals("UNMATCHED", log.getStatusBefore());
        assertEquals("MANUAL_MATCHED", log.getStatusAfter());
    }

    @Test
    void rejectMatch_应回落UNMATCHED并释放日记账() {
        // 驳回只作用于「人工确认」态（MANUAL_MATCHED）；自动匹配 MATCHED 不属于本操作面
        BankStatementEntity stmt = newStatement("MANUAL_MATCHED");
        BankJournalEntity journal = newJournal(true);

        BankReconciliationService.ConfirmResult result =
                reconService.rejectMatch(stmt.getId(), journal.getId(), "tester02");

        assertEquals("UNMATCHED", result.newStatus());

        BankStatementEntity reloaded = statementMapper.selectById(stmt.getId());
        assertEquals("UNMATCHED", reloaded.getMatchStatus(), "驳回后应回到未匹配");
        assertNull(reloaded.getMatchedJournalId(), "驳回后必须清空关联日记账，否则残留错误关联");

        BankJournalEntity reloadedJournal = journalMapper.selectById(journal.getId());
        assertFalse(Boolean.TRUE.equals(reloadedJournal.getIsReconciled()),
                "驳回后日记账应释放为未对账");

        BankReconciliationLogEntity log = reconLogMapper.selectOne(
                new LambdaQueryWrapper<BankReconciliationLogEntity>()
                        .eq(BankReconciliationLogEntity::getStatementId, stmt.getId())
                        .last("LIMIT 1"));
        assertNotNull(log, "驳回也必须留审计日志");
        createdLogIds.add(log.getId());
        assertEquals("REJECT", log.getAction());
    }

    // ===== 负向：不该做的没做 =====

    @Test
    void confirmMatch_流水不存在应报错而非静默成功() {
        BankJournalEntity journal = newJournal(false);
        assertThrows(BusinessException.class,
                () -> reconService.confirmMatch(99999999L, journal.getId(), "tester03"),
                "流水不存在必须报错 —— 静默成功正是本缺陷的形态");
    }

    @Test
    void confirmMatch_日记账不存在应报错且不落库() {
        BankStatementEntity stmt = newStatement("UNMATCHED");
        assertThrows(BusinessException.class,
                () -> reconService.confirmMatch(stmt.getId(), 99999999L, "tester04"));

        BankStatementEntity reloaded = statementMapper.selectById(stmt.getId());
        assertEquals("UNMATCHED", reloaded.getMatchStatus(),
                "校验失败时流水状态不得被改动");
        assertNull(reloaded.getMatchedJournalId(), "校验失败时不得写入关联 id");
    }

    // ===== D8：PENDING_CONFIRM 中间态（原 CHECK 不含此值，属 D4 衍生缺陷）=====

    @Test
    void rejectMatch_PENDING_CONFIRM中间态应可人工驳回() {
        // runMatching 的 60-84 分档产出 PENDING_CONFIRM（待人工决策），
        // V162 已把该值纳入 chk_stmt_match_status 允许集，此处锁死「可被驳回」
        BankStatementEntity stmt = newStatement("PENDING_CONFIRM");
        BankJournalEntity journal = newJournal(false);

        BankReconciliationService.ConfirmResult result =
                reconService.rejectMatch(stmt.getId(), journal.getId(), "tester05");

        assertEquals("UNMATCHED", result.newStatus());
        BankStatementEntity reloaded = statementMapper.selectById(stmt.getId());
        assertEquals("UNMATCHED", reloaded.getMatchStatus(),
                "PENDING_CONFIRM 必须可人工驳回落回 UNMATCHED，否则自动匹配产物无法人工干预（人审铁律 #1）");

        BankReconciliationLogEntity log = reconLogMapper.selectOne(
                new LambdaQueryWrapper<BankReconciliationLogEntity>()
                        .eq(BankReconciliationLogEntity::getStatementId, stmt.getId())
                        .last("LIMIT 1"));
        assertNotNull(log, "PENDING_CONFIRM 驳回也必须留审计日志");
        createdLogIds.add(log.getId());
        assertEquals("PENDING_CONFIRM", log.getStatusBefore(),
                "审计日志 status_before 必须还原驳回前的真实中间态");
        assertEquals("REJECT", log.getAction());
    }

    @Test
    void confirmMatch_PENDING_CONFIRM中间态应可人工确认() {
        BankStatementEntity stmt = newStatement("PENDING_CONFIRM");
        BankJournalEntity journal = newJournal(false);

        BankReconciliationService.ConfirmResult result =
                reconService.confirmMatch(stmt.getId(), journal.getId(), "tester06");

        assertEquals("MANUAL_MATCHED", result.newStatus());
        BankStatementEntity reloaded = statementMapper.selectById(stmt.getId());
        assertEquals("MANUAL_MATCHED", reloaded.getMatchStatus());
        assertEquals(journal.getId(), reloaded.getMatchedJournalId());
    }
}
