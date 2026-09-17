package com.huicai.sme.asset.controller;

import com.huicai.common.response.R;
import com.huicai.sme.asset.service.AssetReportService;
import com.huicai.sme.asset.service.AssetReportService.AssetCategorySummaryVO;
import com.huicai.sme.asset.service.AssetReportService.AssetDepreciationSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 折旧与资产统计报表（P77）
 * <p>
 * 只读聚合端点，绝不触发计提（铁律#1：人是唯一审核主体；折旧计提走既有
 * {@code POST /asset-cards/depreciate-period}，本报表不代跑）。
 * <pre>
 * GET /api/sme/asset/v1/asset-reports/category-summary?period=202606[&categoryId=3]
 * GET /api/sme/asset/v1/asset-reports/depreciation-summary?periodFrom=202601&periodTo=202606[&groupBy=DEPT]
 * GET /api/sme/asset/v1/asset-reports/category-summary/export
 * GET /api/sme/asset/v1/asset-reports/depreciation-summary/export
 * </pre>
 * 数据权限由 {@code EnterpriseDataPermissionInterceptor} 注入（t_asset_* 继承 BaseEntity）。
 */
@Tag(name = "资产折旧与统计报表")
@RestController
@RequestMapping("/api/sme/asset/v1/asset-reports")
@RequiredArgsConstructor
public class AssetReportController {

    private final AssetReportService reportService;

    @Operation(summary = "报表A：资产分类汇总（家底快照，类别 × 数量/原值/累计折旧/净值/本期已提/净值率）")
    @GetMapping("/category-summary")
    public R<AssetCategorySummaryVO> categorySummary(
            @RequestParam String period,
            @RequestParam(required = false) Long categoryId) {
        return R.ok(reportService.getCategorySummary(period, categoryId));
    }

    @Operation(summary = "报表B：折旧计提汇总（期间区间 × 部门/类别，期初+计提=期末恒等式校验）")
    @GetMapping("/depreciation-summary")
    public R<AssetDepreciationSummaryVO> depreciationSummary(
            @RequestParam String periodFrom,
            @RequestParam String periodTo,
            @RequestParam(defaultValue = "CATEGORY") String groupBy) {
        return R.ok(reportService.getDepreciationSummary(periodFrom, periodTo, groupBy));
    }

    @Operation(summary = "报表A 导出（hutool ExcelUtil）")
    @GetMapping("/category-summary/export")
    public void exportCategorySummary(
            @RequestParam String period,
            @RequestParam(required = false) Long categoryId,
            HttpServletResponse response) {
        reportService.exportCategorySummary(period, categoryId, response);
    }

    @Operation(summary = "报表B 导出（hutool ExcelUtil）")
    @GetMapping("/depreciation-summary/export")
    public void exportDepreciationSummary(
            @RequestParam String periodFrom,
            @RequestParam String periodTo,
            @RequestParam(defaultValue = "CATEGORY") String groupBy,
            HttpServletResponse response) {
        reportService.exportDepreciationSummary(periodFrom, periodTo, groupBy, response);
    }
}
