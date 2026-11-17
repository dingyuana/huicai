package com.huicai.common.util;

import com.huicai.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link PeriodRange} 口径测试（SPEC-P151）。
 *
 * <p>PeriodRange 是账期过滤的唯一口径；前端有镜像实现 {@code periodRange()}
 * （frontend/src/utils/period.ts），两端口径必须逐条一致 ——
 * 本测试的期望值与前端 period.utils.test.ts 的断言一一对应，
 * 任何一侧改动（如闰年口径漂移）都会在对应测试显红。</p>
 */
class PeriodRangeTest {

    @Test
    void of_普通月份_月首到月末闭区间() {
        PeriodRange.Range r = PeriodRange.of("202407");
        assertEquals(LocalDate.of(2024, 7, 1), r.start());
        assertEquals(LocalDate.of(2024, 7, 31), r.end());
    }

    @Test
    void of_闰年二月_月末29日() {
        PeriodRange.Range r = PeriodRange.of("202402");
        assertEquals(LocalDate.of(2024, 2, 29), r.end());
    }

    @Test
    void of_平年二月_月末28日() {
        PeriodRange.Range r = PeriodRange.of("202302");
        assertEquals(LocalDate.of(2023, 2, 28), r.end());
    }

    @Test
    void of_月份个位_前导零保留() {
        PeriodRange.Range r = PeriodRange.of("202403");
        assertEquals(LocalDate.of(2024, 3, 1), r.start());
        assertEquals(LocalDate.of(2024, 3, 31), r.end());
    }

    @Test
    void of_空值_两端不设限() {
        assertNullOf(PeriodRange.of(null));
        assertNullOf(PeriodRange.of(""));
        assertNullOf(PeriodRange.of("  "));
    }

    private void assertNullOf(PeriodRange.Range r) {
        assertEquals(null, r.start());
        assertEquals(null, r.end());
    }

    @Test
    void of_非法_非6位_400不静默退化() {
        BusinessException e = assertThrows(BusinessException.class, () -> PeriodRange.of("20247"));
        assertEquals(400, e.getCode());
    }

    @Test
    void of_非法_月份13_400() {
        BusinessException e = assertThrows(BusinessException.class, () -> PeriodRange.of("202413"));
        assertEquals(400, e.getCode());
    }

    @Test
    void of_非法_月份00_400() {
        BusinessException e = assertThrows(BusinessException.class, () -> PeriodRange.of("202400"));
        assertEquals(400, e.getCode());
    }

    @Test
    void of_非法_非数字_400() {
        BusinessException e = assertThrows(BusinessException.class, () -> PeriodRange.of("abcdef"));
        assertEquals(400, e.getCode());
    }

    @Test
    void resolve_period优先于显式日期区间() {
        LocalDate d1 = LocalDate.of(2024, 6, 1);
        LocalDate d2 = LocalDate.of(2024, 9, 30);
        PeriodRange.Range r = PeriodRange.resolve("202407", d1, d2);
        // 落在 period 的月首/月末，而非调用方给的更宽区间
        assertEquals(LocalDate.of(2024, 7, 1), r.start());
        assertEquals(LocalDate.of(2024, 7, 31), r.end());
    }

    @Test
    void resolve_无period_透传日期区间() {
        LocalDate d1 = LocalDate.of(2024, 6, 1);
        LocalDate d2 = LocalDate.of(2024, 9, 30);
        PeriodRange.Range r = PeriodRange.resolve(null, d1, d2);
        assertEquals(d1, r.start());
        assertEquals(d2, r.end());
    }

    @Test
    void resolve_两者皆无_两端不设限() {
        PeriodRange.Range r = PeriodRange.resolve(null, null, null);
        assertEquals(null, r.start());
        assertEquals(null, r.end());
    }

    @Test
    void resolve_blank_period等价于null() {
        LocalDate d1 = LocalDate.of(2024, 6, 1);
        PeriodRange.Range r = PeriodRange.resolve(" ", d1, null);
        assertEquals(d1, r.start());
        assertEquals(null, r.end());
    }
}
