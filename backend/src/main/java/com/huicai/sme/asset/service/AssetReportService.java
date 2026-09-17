package com.huicai.sme.asset.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 折旧与资产统计报表服务（P77）
 * <p>
 * 输出两类只读聚合视图（不触发任何计提动作，铁律#1：人是唯一审核主体）：
 * <ul>
 *   <li>报表A 资产分类汇总（家底快照）：类别 × 数量/原值/累计折旧/净值/本期已提/净值率</li>
 *   <li>报表B 折旧计提汇总：期间区间 × 部门/类别，期初累计 + 区间计提 = 期末累计（恒等式校验）</li>
 * </ul>
 * 口径（对齐 SPEC P77 V1.0，代码实证 2026-09-17）：
 * <ul>
 *   <li>数据源 t_asset_card（status ∈ IN_USE/IDLE/STOPPED，排除 DISPOSED/SCRAPPED/DRAFT）+ t_asset_depreciation</li>
 *   <li>本期已提 currentDepreciation：t_asset_depreciation 该期间实提金额合计（读已计提结果；
 *       未跑 depreciatePeriod 的期间为 0，报表只读不代计提）</li>
 *   <li>期初/期末累计：按资产取期间前/末最近一条 t_asset_depreciation 的 accumulatedDepreciation；
 *       恒等式 opening + depreciated == closing 逐行校验，失败标 consistent=false 不阻断</li>
 * </ul>
 * 数据权限：t_asset_card / t_asset_depreciation / t_asset_category 继承 BaseEntity（enterprise_id），
 * 由 {@code EnterpriseDataPermissionInterceptor} 自动注入；t_dept 为共享表（维度名查用）。
 */
public interface AssetReportService {

    /**
     * 报表A：资产分类汇总
     *
     * @param period     YYYYMM（必填，本期已提折旧的计提期）
     * @param categoryId 可选，按类别过滤
     */
    AssetCategorySummaryVO getCategorySummary(String period, Long categoryId);

    /**
     * 报表B：折旧计提汇总
     *
     * @param periodFrom YYYYMM 区间起（含）
     * @param periodTo   YYYYMM 区间止（含，≥ periodFrom）
     * @param groupBy    CATEGORY(默认)/DEPT/DEPT_CATEGORY
     */
    AssetDepreciationSummaryVO getDepreciationSummary(String periodFrom, String periodTo, String groupBy);

    /** 报表A 导出（hutool ExcelUtil，对齐 ReportServiceImpl 模式） */
    void exportCategorySummary(String period, Long categoryId, jakarta.servlet.http.HttpServletResponse response);

    /** 报表B 导出 */
    void exportDepreciationSummary(String periodFrom, String periodTo, String groupBy,
                                   jakarta.servlet.http.HttpServletResponse response);

    /** 报表A 一行一类别 */
    record AssetCategorySummaryRowVO(
        Long categoryId, String categoryName,
        int qty,
        BigDecimal originalValue,
        BigDecimal accumulatedDepreciation,
        BigDecimal netValue,
        BigDecimal currentDepreciation,
        BigDecimal netRatio   // netValue/originalValue；originalValue=0 时 null
    ) {}

    /** 报表A 汇总 VO */
    record AssetCategorySummaryVO(
        String period,
        List<AssetCategorySummaryRowVO> rows,
        int totalQty,
        BigDecimal totalOriginalValue,
        BigDecimal totalNetValue
    ) {}

    /** 报表B 一行一维度 */
    record AssetDepreciationRowVO(
        String dimKey,
        Long deptId, String deptName,
        Long categoryId, String categoryName,
        int assetCount,
        BigDecimal depreciated,
        BigDecimal openingAccumulated,
        BigDecimal closingAccumulated
    ) {}

    /** 报表B 汇总 VO */
    record AssetDepreciationSummaryVO(
        String periodFrom, String periodTo, String groupBy,
        List<AssetDepreciationRowVO> rows,
        BigDecimal totalDepreciated,
        boolean consistent
    ) {}
}
