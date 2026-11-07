package com.huicai.common.util;

import com.huicai.common.exception.BusinessException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 会计期间（YYYYMM）与日期区间的互转 —— 账期过滤的唯一口径。
 *
 * <p><b>为什么需要它</b>：多数「单据」表（{@code t_bank_statement} 等）不存 period 列，
 * 期间只能由业务日期（{@code tx_date}）推导。各查询端点此前只有可选的
 * {@code startDate/endDate}，前端不传就是<b>全量</b>；把「按账期查」这件事散着写在
 * 每个端点里，必然出现「这个端点记得限期间、那个忘了」的漂移。</p>
 *
 * <p><b>口径</b>：{@code period=YYYYMM} 展开为该月首日与月末日（闭区间），
 * 与 {@code tx_date} 的 {@code BETWEEN} 语义一致；
 * 格式非法直接 400，<b>不静默退化成「不过滤」</b> ——
 * 后者会让「本以为是本月、实际是全表」这类错误继续无声存在（AGENTS §4.5 静默失败）。</p>
 */
public final class PeriodRange {

    /** 后端接口的期间入参格式：与 t_period.period_code / currentPeriod 一致的 6 位 YYYYMM */
    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    private PeriodRange() {
    }

    /** 解析后的闭区间；{@code start}/{@code end} 为 null 表示该侧不设限。 */
    public record Range(LocalDate start, LocalDate end) {
    }

    /**
     * 把 {@code period} 展开成月首/月末。
     *
     * @throws BusinessException 400 —— 非空但不是 6 位 YYYYMM
     */
    public static Range of(String period) {
        if (period == null || period.isBlank()) {
            return new Range(null, null);
        }
        String p = period.trim();
        if (p.length() != 6) {
            throw BusinessException.badRequest("期间格式应为 YYYYMM（如 202407），实际=" + period);
        }
        try {
            YearMonth ym = YearMonth.parse(p, PERIOD_FMT);
            return new Range(ym.atDay(1), ym.atEndOfMonth());
        } catch (DateTimeParseException e) {
            throw BusinessException.badRequest("期间格式应为 YYYYMM（如 202407），实际=" + period);
        }
    }

    /**
     * 解析查询的日期边界：<b>{@code period} 优先于显式日期区间</b>。
     *
     * <p>两者同时出现时以 period 为准（它更窄、语义更明确），端点文档已写明；
     * 只给日期时按给定区间；都不给时返回不设限（保持既有端点行为，
     * 「默认只看当前账期」属 UI 默认值，不在服务端偷偷加过滤 ——
     * 否则任何未传期间的调用方都会莫名其妙少数据）。</p>
     */
    public static Range resolve(String period, LocalDate startDate, LocalDate endDate) {
        if (period != null && !period.isBlank()) {
            return of(period);
        }
        return new Range(startDate, endDate);
    }
}
