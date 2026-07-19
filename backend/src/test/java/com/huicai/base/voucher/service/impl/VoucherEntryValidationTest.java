package com.huicai.base.voucher.service.impl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 借借贷平衡与金额校验的纯单元测试
 * 覆盖 VoucherServiceImpl.validateEntries 的核心规则
 */
class VoucherEntryValidationTest {

    /**
     * 复制 VoucherServiceImpl 中 validateEntries 的纯逻辑,
     * 仅测试借贷平衡/非负/全零校验(无 Spring 依赖)
     */
    static void validateEntries(BigDecimal[] debits, BigDecimal[] credits) {
        if (debits.length < 2) throw new IllegalArgumentException("至少2条分录");
        BigDecimal totalD = BigDecimal.ZERO;
        BigDecimal totalC = BigDecimal.ZERO;
        for (int i = 0; i < debits.length; i++) {
            BigDecimal d = debits[i] == null ? BigDecimal.ZERO : debits[i];
            BigDecimal c = credits[i] == null ? BigDecimal.ZERO : credits[i];
            if (d.signum() < 0 || c.signum() < 0)
                throw new IllegalArgumentException("第" + (i + 1) + "条金额为负");
            if (d.signum() == 0 && c.signum() == 0)
                throw new IllegalArgumentException("第" + (i + 1) + "条全零");
            totalD = totalD.add(d);
            totalC = totalC.add(c);
        }
        if (totalD.compareTo(totalC) != 0)
            throw new IllegalArgumentException("借贷不平衡");
    }

    @Test
    void balanced_two_entries_passes() {
        assertDoesNotThrow(() -> validateEntries(
                new BigDecimal[]{new BigDecimal("100.00"), new BigDecimal("0")},
                new BigDecimal[]{new BigDecimal("0"), new BigDecimal("100.00")}));
    }

    @Test
    void unbalanced_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> validateEntries(
                        new BigDecimal[]{new BigDecimal("100.00"), new BigDecimal("0")},
                        new BigDecimal[]{new BigDecimal("0"), new BigDecimal("50.00")}));
        assertTrue(ex.getMessage().contains("借贷不平衡"));
    }

    @Test
    void negative_amount_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> validateEntries(
                        new BigDecimal[]{new BigDecimal("-1"), new BigDecimal("0")},
                        new BigDecimal[]{new BigDecimal("0"), new BigDecimal("1")}));
        assertTrue(ex.getMessage().contains("为负"));
    }

    @Test
    void all_zero_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> validateEntries(
                        new BigDecimal[]{new BigDecimal("100.00"), new BigDecimal("0")},
                        new BigDecimal[]{new BigDecimal("0"), new BigDecimal("0")}));
        assertTrue(ex.getMessage().contains("全零"));
    }

    @Test
    void less_than_two_entries_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> validateEntries(
                        new BigDecimal[]{new BigDecimal("100.00")},
                        new BigDecimal[]{new BigDecimal("100.00")}));
        assertTrue(ex.getMessage().contains("至少2条"));
    }

    @Test
    void three_way_balance_passes() {
        assertDoesNotThrow(() -> validateEntries(
                new BigDecimal[]{new BigDecimal("100.00"), new BigDecimal("200.00"), new BigDecimal("0")},
                new BigDecimal[]{new BigDecimal("0"), new BigDecimal("0"), new BigDecimal("300.00")}));
    }
}
