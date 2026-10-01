package com.huicai.sme.cash.mapper;

import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * CashJournalMapper 自定义方法真库测试（REQ-2026-131 / P104 第 4 批）
 *
 * <p>取代原 {@code CashJournalMapperTest}（mock 被测对象本身）。三个自定义方法都是
 * 聚合查询，mock 版对它们<b>一个都测不到</b>。
 *
 * <p>注意：夹具采用「先造好再只查一次」而非「查→改→再查」——
 * 同一 {@code SqlSession} 内相同 SQL+参数会命中 MyBatis 一级缓存
 * （与 AGENTS §4.3 第 12 条同源现象），那样第二次查询拿到的是旧值。
 */
@DisplayName("P104 CashJournalMapper 自定义方法真库")
class CashJournalMapperRealDBTest extends AbstractMapperTest {

    @Autowired
    private CashJournalMapper cashJournalMapper;

    private static final String PERIOD = "209912";

    private Long insertEntry(String period, String debit, String credit, String balance) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_cash_journal (period, journal_date, journal_no, debit, credit, "
                        + "balance, enterprise_id, deleted) "
                        + "VALUES (?, CURRENT_DATE, ?, ?, ?, ?, 1, 0) RETURNING id",
                Long.class, period, "P104CJ" + System.nanoTime() % 1000000,
                new BigDecimal(debit), new BigDecimal(credit), new BigDecimal(balance));
    }

    @Test
    @DisplayName("sumDebitByPeriod / sumCreditByPeriod 各自求和")
    void debitAndCreditAreSummedSeparately() {
        insertEntry(PERIOD, "100.00", "0.00", "100.00");
        insertEntry(PERIOD, "250.50", "0.00", "350.50");

        BigDecimal debit = cashJournalMapper.sumDebitByPeriod(PERIOD);
        assertEquals(0, new BigDecimal("350.50").compareTo(debit),
                "借方合计应为 350.50，实际=" + debit);

        insertEntry(PERIOD, "0.00", "80.00", "270.50");
        BigDecimal credit = cashJournalMapper.sumCreditByPeriod(PERIOD);
        assertEquals(0, new BigDecimal("80.00").compareTo(credit),
                "贷方合计应为 80.00，实际=" + credit);
    }

    @Test
    @DisplayName("空期间返回 0 而非 null（COALESCE 生效）")
    void emptyPeriodReturnsZeroNotNull() {
        assertEquals(0, BigDecimal.ZERO.compareTo(cashJournalMapper.sumDebitByPeriod("190001")),
                "无数据期间的借方合计应为 0");
        assertEquals(0, BigDecimal.ZERO.compareTo(cashJournalMapper.sumCreditByPeriod("190001")),
                "无数据期间的贷方合计应为 0");
    }

    @Test
    @DisplayName("getLastBalance 取该期间最后一条的余额")
    void getLastBalanceTakesLatestRow() {
        insertEntry(PERIOD, "100.00", "0.00", "100.00");
        insertEntry(PERIOD, "50.00", "0.00", "150.00");
        insertEntry(PERIOD, "0.00", "70.00", "80.00");

        BigDecimal last = cashJournalMapper.getLastBalance(PERIOD);
        assertEquals(0, new BigDecimal("80.00").compareTo(last),
                "应取最后一条的余额 80.00，实际=" + last);
    }

    @Test
    @DisplayName("无数据期间 getLastBalance 返回 null")
    void getLastBalanceIsNullWhenNoRows() {
        assertNull(cashJournalMapper.getLastBalance("190002"), "无数据期间不应返回余额");
    }

    @Test
    @DisplayName("反证：软删除的行不参与三个聚合")
    void softDeletedExcludedFromAggregates() {
        insertEntry(PERIOD, "10.00", "0.00", "10.00");
        Long dead = insertEntry(PERIOD, "777.00", "0.00", "787.00");
        jdbcTemplate.update("UPDATE t_cash_journal SET deleted = 1 WHERE id = ?", dead);

        BigDecimal debit = cashJournalMapper.sumDebitByPeriod(PERIOD);
        assertEquals(0, new BigDecimal("10.00").compareTo(debit),
                "软删的 777.00 仍被计入，实际=" + debit);
    }
}