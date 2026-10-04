package com.huicai.base.system.dto;

import com.huicai.base.system.entity.PeriodEntity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 会计期间新增/修改入参（REQ-2026-129 / P102 DTO 隔离 批次③）。
 *
 * <p><b>为何必须有 DTO</b>：原端点直接 {@code @RequestBody PeriodEntity}，而
 * {@code chk_period_status} 的允许集是<b>小写</b> {@code open/closed/locked}
 * （与其它表的大写不同，AGENTS §4.2 第 14 条），且
 * {@code PeriodServiceImpl#openPeriod/closePeriod/lockPeriod} 会把期间在
 * 这些状态间流转。Entity 直入意味着客户端 POST {@code status=locked}
 * 可**直接创建出「已锁定的会计期间」**，阻断该期间全部记账 —— 且绕过了
 * 「建期间 → 开账」的人工流程。
 *
 * <p>同时剔除 {@code periodCode}（由 {@code PeriodServiceImpl#save} 按
 * year/month 或 startDate 生成，DB 侧 NOT NULL 无默认值但属派生列）、
 * 以及 {@code openedAt/openedBy/openedByName/openingStatus}（开账动作产生）。
 */
@Data
public class PeriodSaveDTO {

    @NotNull(message = "年度不能为空")
    @Min(value = 2000, message = "年度不合法")
    @Max(value = 2999, message = "年度不合法")
    private Integer year;

    @NotNull(message = "月份不能为空")
    @Min(value = 1, message = "月份必须在 1~12 之间")
    @Max(value = 12, message = "月份必须在 1~12 之间")
    private Integer month;

    /** 起始日期，留空则由 Service 按 year/month 取当月第一天 */
    private LocalDate startDate;

    /** 结束日期，留空则由 Service 取当月最后一天 */
    private LocalDate endDate;

    public PeriodEntity toEntity() {
        PeriodEntity e = new PeriodEntity();
        e.setYear(year);
        e.setMonth(month);
        e.setStartDate(startDate);
        e.setEndDate(endDate);
        return e;
    }
}
