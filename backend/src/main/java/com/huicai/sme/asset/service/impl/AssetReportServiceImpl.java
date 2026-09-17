package com.huicai.sme.asset.service.impl;

import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.system.entity.DeptEntity;
import com.huicai.base.system.mapper.DeptMapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.asset.entity.AssetCardEntity;
import com.huicai.sme.asset.entity.AssetCategoryEntity;
import com.huicai.sme.asset.entity.AssetDepreciationEntity;
import com.huicai.sme.asset.mapper.AssetCardMapper;
import com.huicai.sme.asset.mapper.AssetCategoryMapper;
import com.huicai.sme.asset.mapper.AssetDepreciationMapper;
import com.huicai.sme.asset.service.AssetReportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 折旧与资产统计报表实现（P77）。
 * <p>
 * 纯只读聚合：两次 SQL 读（卡片 + 折旧流水）+ 内存分组，绝不触发计提。
 * 口径对齐 SPEC P77 V1.0（代码实证 2026-09-17）：
 * <ul>
 *   <li>有效资产 = status ∉ DISPOSED/SCRAPPED/DRAFT（{@code EXCLUDED} 负向集合，
 *       即保留 IN_USE/IDLE/STOPPED 活跃卡片）</li>
 *   <li>报表A currentDepreciation = t_asset_depreciation 入参期间实提合计</li>
 *   <li>报表B 期初/期末 = 按资产取期间前/末最近一条累计；opening + 区间计提 == closing，不一致标 consistent=false</li>
 * </ul>
 * 数据权限：卡片/折旧/类别实体继承 BaseEntity（enterprise_id），拦截器自动注入；t_dept 共享表仅维度名查。
 * dual-defense：mock 单测下 wrapper 会被绕过，故拿到 list 后按状态集合再过滤一次。
 */
@Service
public class AssetReportServiceImpl implements AssetReportService {

    private static final Set<String> EXCLUDED = Set.of("DISPOSED", "SCRAPPED", "DRAFT");

    private final AssetCardMapper cardMapper;
    private final AssetDepreciationMapper depreciationMapper;
    private final AssetCategoryMapper categoryMapper;
    private final DeptMapper deptMapper;

    public AssetReportServiceImpl(AssetCardMapper cardMapper,
                                  AssetDepreciationMapper depreciationMapper,
                                  AssetCategoryMapper categoryMapper,
                                  DeptMapper deptMapper) {
        this.cardMapper = cardMapper;
        this.depreciationMapper = depreciationMapper;
        this.categoryMapper = categoryMapper;
        this.deptMapper = deptMapper;
    }

    // ───────────────────────── 报表A ─────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AssetCategorySummaryVO getCategorySummary(String period, Long categoryId) {
        requirePeriod(period);

        List<AssetCardEntity> cards = validCards(categoryId);
        Map<Long, String> catName = categoryNameMap();
        // 本期已提：入参期间 t_asset_depreciation 实提合计，按 assetId 归到类别
        Map<Long, BigDecimal> currentByAsset = currentDepreciationByAsset(period);
        Map<Long, List<AssetCardEntity>> byCat = cards.stream()
                .collect(Collectors.groupingBy(AssetCardEntity::getCategoryId, TreeMap::new, Collectors.toList()));

        List<AssetCategorySummaryRowVO> rows = new ArrayList<>();
        int totalQty = 0;
        BigDecimal totalOriginal = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        for (Map.Entry<Long, List<AssetCardEntity>> e : byCat.entrySet()) {
            Long catId = e.getKey();
            List<AssetCardEntity> group = e.getValue();
            int qty = group.size();
            BigDecimal original = sum(group.stream().map(AssetCardEntity::getOriginalValue));
            BigDecimal acc = sum(group.stream().map(AssetCardEntity::getAccumulatedDepreciation));
            BigDecimal net = sum(group.stream().map(AssetCardEntity::getNetValue));
            BigDecimal current = sum(group.stream().map(c -> nvl(currentByAsset.get(c.getId()))));
            BigDecimal netRatio = original.signum() == 0 ? null
                    : net.divide(original, 4, RoundingMode.HALF_UP);
            rows.add(new AssetCategorySummaryRowVO(catId, catName.getOrDefault(catId, "-"),
                    qty, original, acc, net, current, netRatio));
            totalQty += qty;
            totalOriginal = totalOriginal.add(original);
            totalNet = totalNet.add(net);
        }
        rows.sort(Comparator.comparing(AssetCategorySummaryRowVO::originalValue,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return new AssetCategorySummaryVO(period, rows, totalQty, totalOriginal, totalNet);
    }

    @Override
    @Transactional(readOnly = true)
    public AssetDepreciationSummaryVO getDepreciationSummary(String periodFrom, String periodTo, String groupBy) {
        requirePeriodRange(periodFrom, periodTo);
        String g = normalizeGroupBy(groupBy);

        List<AssetCardEntity> cards = validCards(null);
        if (cards.isEmpty()) {
            return new AssetDepreciationSummaryVO(periodFrom, periodTo, g, List.of(), BigDecimal.ZERO, true);
        }
        List<Long> ids = cards.stream().map(AssetCardEntity::getId).toList();
        // 一次性读这些资产的全部折旧流水（内存分桶；dual-defense：Java 侧再按 assetId 过滤）
        Map<Long, List<AssetDepreciationEntity>> depByAsset = loadDepreciation(ids);
        Map<Long, String> deptName = deptNameMap();
        Map<Long, String> catName = categoryNameMap();

        Map<String, List<AssetCardEntity>> byDim = cards.stream()
                .collect(Collectors.groupingBy(c -> dimKey(g, c), () -> new TreeMap<>(), Collectors.toList()));

        List<AssetDepreciationRowVO> rows = new ArrayList<>();
        BigDecimal totalDep = BigDecimal.ZERO;
        boolean consistent = true;
        for (Map.Entry<String, List<AssetCardEntity>> e : byDim.entrySet()) {
            List<AssetCardEntity> group = e.getValue();
            BigDecimal opening = BigDecimal.ZERO;
            BigDecimal closing = BigDecimal.ZERO;
            BigDecimal dep = BigDecimal.ZERO;
            for (AssetCardEntity c : group) {
                List<AssetDepreciationEntity> recs = depByAsset.getOrDefault(c.getId(), List.of());
                // 期初：period < periodFrom 的最大 period 的累计；无则 0
                opening = opening.add(latestAccumulated(recs, p -> p.compareTo(periodFrom) < 0, c, false));
                // 期末：period <= periodTo 的最大 period 的累计；无则卡片当前 accumulatedDepreciation
                closing = closing.add(latestAccumulated(recs, p -> p.compareTo(periodTo) <= 0, c, true));
                // 区间计提：periodFrom <= period <= periodTo 的 depreciationAmount 合计
                dep = dep.add(sum(recs.stream()
                        .filter(r -> within(r.getPeriod(), periodFrom, periodTo))
                        .map(AssetDepreciationEntity::getDepreciationAmount)));
            }
            totalDep = totalDep.add(dep);
            // 恒等式：opening + dep == closing
            if (opening.add(dep).compareTo(closing) != 0) {
                consistent = false;
            }
            Long deptId;
            Long catId;
            if ("DEPT".equals(g)) {
                deptId = Long.valueOf(e.getKey());
                catId = null;
            } else if ("DEPT_CATEGORY".equals(g)) {
                String[] parts = e.getKey().split("\\|");
                deptId = Long.valueOf(parts[0]);
                catId = Long.valueOf(parts[1]);
            } else { // CATEGORY
                deptId = null;
                catId = Long.valueOf(e.getKey());
            }
            rows.add(new AssetDepreciationRowVO(e.getKey(), deptId,
                    deptId == null ? "-" : deptName.getOrDefault(deptId, "-"),
                    catId, catId == null ? "-" : catName.getOrDefault(catId, "-"),
                    group.size(), dep, opening, closing));
        }
        rows.sort(Comparator.comparing(AssetDepreciationRowVO::depreciated,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return new AssetDepreciationSummaryVO(periodFrom, periodTo, g, rows, totalDep, consistent);
    }

    // ───────────────────────── 导出 ─────────────────────────

    @Override
    @Transactional(readOnly = true)
    public void exportCategorySummary(String period, Long categoryId, HttpServletResponse response) {
        AssetCategorySummaryVO vo = getCategorySummary(period, categoryId);
        List<List<Object>> data = new ArrayList<>();
        for (AssetCategorySummaryRowVO r : vo.rows()) {
            data.add(List.of(r.categoryName(), r.qty(), r.originalValue(), r.accumulatedDepreciation(),
                    r.netValue(), r.currentDepreciation(), r.netRatio()));
        }
        List<List<Object>> head = List.of(
                List.of("类别"), List.of("数量"), List.of("原值"), List.of("累计折旧"),
                List.of("净值"), List.of("本期已提"), List.of("净值率"));
        writeXlsx(response, "资产分类汇总_" + period, head, data);
    }

    @Override
    @Transactional(readOnly = true)
    public void exportDepreciationSummary(String periodFrom, String periodTo, String groupBy,
                                          HttpServletResponse response) {
        AssetDepreciationSummaryVO vo = getDepreciationSummary(periodFrom, periodTo, groupBy);
        List<List<Object>> data = new ArrayList<>();
        for (AssetDepreciationRowVO r : vo.rows()) {
            data.add(List.of(r.dimKey(), r.deptName(), r.categoryName(), r.assetCount(),
                    r.depreciated(), r.openingAccumulated(), r.closingAccumulated()));
        }
        List<List<Object>> head = List.of(
                List.of("维度"), List.of("部门"), List.of("类别"), List.of("资产数"),
                List.of("区间计提"), List.of("期初累计"), List.of("期末累计"));
        writeXlsx(response, "折旧计提汇总_" + periodFrom + "-" + periodTo, head, data);
    }

    // ───────────────────────── 私有辅助 ─────────────────────────

    /** 查有效资产卡片（状态二次过滤 dual-defense） */
    private List<AssetCardEntity> validCards(Long categoryId) {
        LambdaQueryWrapper<AssetCardEntity> w = new LambdaQueryWrapper<AssetCardEntity>()
                .ne(AssetCardEntity::getStatus, "DISPOSED")
                .ne(AssetCardEntity::getStatus, "SCRAPPED")
                .ne(AssetCardEntity::getStatus, "DRAFT");
        if (categoryId != null) {
            w.eq(AssetCardEntity::getCategoryId, categoryId);
        }
        List<AssetCardEntity> all = cardMapper.selectList(w);
        return all.stream().filter(c -> !EXCLUDED.contains(c.getStatus())).toList();
    }

    /** 入参期间 t_asset_depreciation 实提合计，按 assetId 分桶 */
    private Map<Long, BigDecimal> currentDepreciationByAsset(String period) {
        LambdaQueryWrapper<AssetDepreciationEntity> w = new LambdaQueryWrapper<AssetDepreciationEntity>()
                .eq(AssetDepreciationEntity::getPeriod, period);
        List<AssetDepreciationEntity> recs = depreciationMapper.selectList(w);
        Map<Long, BigDecimal> map = new HashMap<>();
        for (AssetDepreciationEntity r : recs) {
            if (period.equals(r.getPeriod())) { // dual-defense
                map.merge(r.getAssetId(), nvl(r.getDepreciationAmount()), BigDecimal::add);
            }
        }
        return map;
    }

    /** 批量读多资产的折旧流水，按 assetId 分桶 */
    private Map<Long, List<AssetDepreciationEntity>> loadDepreciation(List<Long> assetIds) {
        if (assetIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<AssetDepreciationEntity> w = new LambdaQueryWrapper<AssetDepreciationEntity>()
                .in(AssetDepreciationEntity::getAssetId, assetIds);
        List<AssetDepreciationEntity> recs = depreciationMapper.selectList(w);
        Map<Long, List<AssetDepreciationEntity>> map = new HashMap<>();
        for (AssetDepreciationEntity r : recs) {
            if (assetIds.contains(r.getAssetId())) { // dual-defense
                map.computeIfAbsent(r.getAssetId(), k -> new ArrayList<>()).add(r);
            }
        }
        return map;
    }

    /** 取满足谓词的最大 period 的累计折旧；无匹配则 fallback（期末用卡片当前累计，期初用 0） */
    private BigDecimal latestAccumulated(List<AssetDepreciationEntity> recs,
                                         Function<String, Boolean> pred,
                                         AssetCardEntity card, boolean useCardFallback) {
        return recs.stream()
                .filter(r -> pred.apply(r.getPeriod()))
                .max(Comparator.comparing(AssetDepreciationEntity::getPeriod))
                .map(AssetDepreciationEntity::getAccumulatedDepreciation)
                .map(this::nvl)
                .orElseGet(() -> useCardFallback ? nvl(card.getAccumulatedDepreciation()) : BigDecimal.ZERO);
    }

    private boolean within(String period, String from, String to) {
        return period != null && period.compareTo(from) >= 0 && period.compareTo(to) <= 0;
    }

    private String dimKey(String g, AssetCardEntity c) {
        return switch (g) {
            case "DEPT" -> String.valueOf(c.getDeptId());
            case "DEPT_CATEGORY" -> c.getDeptId() + "|" + c.getCategoryId();
            default -> String.valueOf(c.getCategoryId());
        };
    }

    private String normalizeGroupBy(String groupBy) {
        String g = groupBy == null ? "CATEGORY" : groupBy.trim().toUpperCase();
        if (!Set.of("CATEGORY", "DEPT", "DEPT_CATEGORY").contains(g)) {
            throw BusinessException.badRequest("P77_003 非法 groupBy: " + groupBy + "（须 CATEGORY/DEPT/DEPT_CATEGORY）");
        }
        return g;
    }

    private Map<Long, String> categoryNameMap() {
        List<AssetCategoryEntity> cats = categoryMapper.selectList(null);
        return cats.stream().collect(Collectors.toMap(AssetCategoryEntity::getId,
                c -> c.getName() == null ? "-" : c.getName(), (a, b) -> a));
    }

    private Map<Long, String> deptNameMap() {
        List<DeptEntity> depts = deptMapper.selectList(null);
        return depts.stream().collect(Collectors.toMap(DeptEntity::getId,
                d -> d.getName() == null ? "-" : d.getName(), (a, b) -> a));
    }

    private void writeXlsx(HttpServletResponse response, String baseName,
                           List<List<Object>> head, List<List<Object>> data) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",
                    "attachment;filename=" + URLEncoder.encode(baseName + ".xlsx", StandardCharsets.UTF_8));
            ExcelWriter writer = ExcelUtil.getWriter(true);
            for (int i = 0; i < head.size(); i++) {
                writer.writeCellValue(i, 0, head.get(i).get(0));
            }
            for (int i = 0; i < data.size(); i++) {
                for (int j = 0; j < data.get(i).size(); j++) {
                    Object v = data.get(i).get(j);
                    writer.writeCellValue(j, i + 1, v == null ? "" : v);
                }
            }
            writer.flush(response.getOutputStream());
            writer.close();
        } catch (Exception e) {
            throw new BusinessException("P77_006 导出 Excel 失败: " + e.getMessage());
        }
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal sum(Stream<BigDecimal> s) {
        return s.map(this::nvl).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void requirePeriod(String period) {
        if (period == null || !period.matches("\\d{6}")) {
            throw BusinessException.badRequest("P77_001 period 须为 6 位数字 YYYYMM: " + period);
        }
        YearMonth.parse(period, DateTimeFormatter.ofPattern("yyyyMM"));
    }

    private void requirePeriodRange(String from, String to) {
        requirePeriod(from);
        requirePeriod(to);
        if (from.compareTo(to) > 0) {
            throw BusinessException.badRequest("P77_004 periodFrom 不能大于 periodTo: " + from + " > " + to);
        }
    }
}
