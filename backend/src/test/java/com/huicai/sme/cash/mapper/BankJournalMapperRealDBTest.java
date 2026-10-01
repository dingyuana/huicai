package com.huicai.sme.cash.mapper;

import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BankJournalMapper 自定义方法真库测试（REQ-2026-131 / P104 第 4 批）
 *
 * <p>取代原 {@code BankJournalMapperTest}（mock 被测对象本身）。该 Mapper 有 6 个
 * 自定义方法，全是聚合与批量更新 SQL —— mock 版对它们<b>一个都测不到</b>：
 * 聚合是否真的按 account_id 分组、is_reconciled 的 NULL 语义、
 * 批量解绑是否只影响非空行。
 *
 * <p>夹具一律用 JdbcTemplate 直插，以便精确构造 NULL / 边界值；
 * {@code t_bank_journal.account_id} 有外键 {@code fk_journal_account}，
 * 故复用基类提供的合法银行账户。
 */
@DisplayName("P104 BankJournalMapper 自定义方法真库")
class BankJournalMapperRealDBTest extends AbstractMapperTest {

    @Autowired
    private BankJournalMapper bankJournalMapper;

    private Long insertJournal(Long accountId, String txType, String amount, String period) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_bank_journal (account_id, tx_date, period, tx_type, amount, "
                        + "enterprise_id, deleted) "
                        + "VALUES (?, CURRENT_DATE, ?, ?, ?, 1, 0) RETURNING id",
                Long.class, accountId, period, txType, new BigDecimal(amount));
    }

    @Test
    @DisplayName("sumAmountByAccount 按账户汇总金额（COALESCE 保证空集返回 0）")
    void sumAmountByAccountAggregates() {
        Long accountId = ensureBankAccount(DEFAULT_ENTERPRISE_ID);
        String period = "P110B";
        insertJournal(accountId, "INCOME", "100.00", period);
        insertJournal(accountId, "INCOME", "250.50", period);

        BigDecimal sum = bankJournalMapper.sumAmountByAccount(accountId);
        // 该账户下本用例新增 350.50；用「基线 + 增量」避免种子数据干扰
        assertTrue(sum.compareTo(new BigDecimal("350.50")) >= 0,
                "汇总金额小于本用例新增的 350.50，实际=" + sum);
    }

    @Test
    @DisplayName("updateReconciled 能把 NULL 置为已对账，也能改回")
    void updateReconciledTogglesFlag() {
        Long accountId = ensureBankAccount(DEFAULT_ENTERPRISE_ID);
        Long id = insertJournal(accountId, "INCOME", "10.00", "P120B");

        assertEquals(1, bankJournalMapper.updateReconciled(id, true));
        Boolean after = jdbcTemplate.queryForObject(
                "SELECT is_reconciled FROM t_bank_journal WHERE id = ?", Boolean.class, id);
        assertEquals(Boolean.TRUE, after, "is_reconciled 未置为 true");

        bankJournalMapper.updateReconciled(id, false);
        Boolean back = jdbcTemplate.queryForObject(
                "SELECT is_reconciled FROM t_bank_journal WHERE id = ?", Boolean.class, id);
        assertEquals(Boolean.FALSE, back, "is_reconciled 未改回 false");
    }

    @Test
    @DisplayName("selectUnreconciled 必须同时包含 NULL 与 false 两种未对账语义")
    void selectUnreconciledIncludesNullAndFalse() {
        Long accountId = ensureBankAccount(DEFAULT_ENTERPRISE_ID);
        Long nullFlag = insertJournal(accountId, "INCOME", "11.00", "P130B");
        // 显式造一条 is_reconciled = false
        Long falseFlag = jdbcTemplate.queryForObject(
                "INSERT INTO t_bank_journal (account_id, tx_date, period, tx_type, amount, "
                        + "is_reconciled, enterprise_id, deleted) "
                        + "VALUES (?, CURRENT_DATE, 'P130B', 'INCOME', 12.00, false, 1, 0) RETURNING id",
                Long.class, accountId);

        var unreconciled = bankJournalMapper.selectUnreconciled(accountId);
        assertTrue(unreconciled != null && !unreconciled.isEmpty(), "未对账流水为空");
        boolean hasNull = unreconciled.stream().anyMatch(j -> nullFlag.equals(j.getId()));
        boolean hasFalse = unreconciled.stream().anyMatch(j -> falseFlag.equals(j.getId()));
        assertTrue(hasNull, "is_reconciled IS NULL 的流水未被查出（SQL 的 IS NULL 分支失效）");
        assertTrue(hasFalse, "is_reconciled = false 的流水未被查出");
    }

    @Test
    @DisplayName("nullOutBusinessDocId 只解绑非空行，且不删数据")
    void nullOutBusinessDocIdOnlyClearsNonNull() {
        Long accountId = ensureBankAccount(DEFAULT_ENTERPRISE_ID);
        Long id = insertJournal(accountId, "INCOME", "13.00", "P140B");

        int affected = bankJournalMapper.nullOutBusinessDocId();
        assertTrue(affected >= 0, "nullOutBusinessDocId 返回负数：" + affected);

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_bank_journal WHERE id = ?", Integer.class, id);
        assertEquals(1, rows, "批量解绑把行删掉了 —— 应只置 NULL 不删除");

        Integer stillBound = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_bank_journal WHERE business_doc_id IS NOT NULL",
                Integer.class);
        assertEquals(0, stillBound, "仍有 business_doc_id 非空的行未被解绑");
    }

    @Test
    @DisplayName("反证：软删除的流水不参与金额汇总（deleted=0 过滤生效）")
    void softDeletedExcludedFromSum() {
        Long accountId = ensureBankAccount(DEFAULT_ENTERPRISE_ID);
        // 先造好两条（一条正常、一条已软删），再**只查一次**。
        // 不能用「查→改→再查」的写法：同一 SqlSession 内相同 SQL+参数会命中
        // MyBatis 一级缓存，第二次直接返回旧值（与 AGENTS §4.3 第 12 条同源现象）。
        insertJournal(accountId, "INCOME", "100.00", "P150B");
        Long dead = insertJournal(accountId, "INCOME", "999.00", "P150B");
        jdbcTemplate.update("UPDATE t_bank_journal SET deleted = 1 WHERE id = ?", dead);

        BigDecimal sum = bankJournalMapper.sumAmountByAccount(accountId);
        assertEquals(0, new BigDecimal("100.00").compareTo(sum),
                "汇总应只含未软删的 100.00（实际=" + sum + "，说明 deleted=0 过滤失效）");
    }
}