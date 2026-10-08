package com.huicai.base.report.service.impl;

import org.springframework.transaction.annotation.Transactional;
import cn.hutool.core.io.IoUtil;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.huicai.base.balance.service.SubjectBalanceService;
import com.huicai.base.report.mapper.ReportDataMapper;
import com.huicai.base.report.service.ReportService;
import com.huicai.base.system.util.SecurityUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
/**
 * 类级事务：P102/M5b —— 走租户表的读写路径必须有事务，否则切面不触发、
 * app.enterprise_id 设不进去，非超级用户下 RLS 会把本企业数据也过滤掉（读 0 行）。
 */
@Transactional
public class ReportServiceImpl implements ReportService {

    private static final String END_BALANCE = "end_balance";
    private static final String BEGIN_BALANCE = "begin_balance";
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    // 科目段兜底判定：subtotal() 求和与逐行 subtotalGroup 标记必须共用同一份，
    // 否则前端按标记做悬空保护时会与实际小计口径不一致。
    private static final java.util.function.Predicate<String> CURRENT_ASSET_CODE = c -> c.startsWith("1")
            && (c.startsWith("10") || c.startsWith("11")
            || c.startsWith("12") || c.startsWith("14"))
            || c.startsWith("50") || c.startsWith("51")
            || c.startsWith("52") || c.startsWith("54");
    private static final java.util.function.Predicate<String> NON_CURRENT_ASSET_CODE = c -> c.startsWith("1")
            && (c.startsWith("13") || c.startsWith("15")
            || c.startsWith("16") || c.startsWith("17") || c.startsWith("18") || c.startsWith("19"))
            || c.startsWith("53");
    private static final java.util.function.Predicate<String> CURRENT_LIABILITY_CODE = c -> c.startsWith("2")
            && (c.startsWith("20") || c.startsWith("21") || c.startsWith("22"));
    private static final java.util.function.Predicate<String> NON_CURRENT_LIABILITY_CODE = c -> c.startsWith("2")
            && (c.startsWith("24") || c.startsWith("25") || c.startsWith("27")
            || c.startsWith("28") || c.startsWith("29"));

    private final ReportDataMapper reportDataMapper;
    private final SubjectBalanceService subjectBalanceService;

    @Override
    public List<Map<String, Object>> subjectBalanceTable(String period) {
        return reportDataMapper.subjectBalance(period);
    }

    /**
     * 报表诊断规则（阶段 E）。全部复用既有查询拼装，不新增 SQL：
     * 收入/费用取 incomeStatement 的段位，现金取 cashSubjectBalance，期初连续性复用 P98 的校验。
     */
    @Override
    public List<Map<String, Object>> diagnostics(String period) {
        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, Object> income = incomeStatement(period);

        BigDecimal revenue = toBigDecimal(income.get("revenue"));
        BigDecimal periodExpense = toBigDecimal(income.get("periodExpense"));
        if (revenue.signum() == 0 && periodExpense.signum() > 0) {
            out.add(diagnostic("R_REVENUE_ZERO", "本期零收入但存在费用",
                    "本期营业收入为 0，而期间费用为 " + periodExpense
                            + "，请确认是否漏记收入或存在挂账费用"));
        }

        Map<String, Object> cash = reportDataMapper.cashSubjectBalance(period);
        BigDecimal beginCash = toBigDecimal(getOrNull(cash, "begin_cash"));
        BigDecimal endCash = toBigDecimal(getOrNull(cash, "end_cash"));
        if (beginCash.signum() > 0 && endCash.compareTo(beginCash.multiply(new BigDecimal("0.5"))) < 0) {
            out.add(diagnostic("R_CASH_DROP", "期末现金较期初骤降",
                    "期初现金 " + beginCash + " → 期末现金 " + endCash + "，降幅超过一半，请核对资金去向"));
        }

        Map<String, Object> continuity = subjectBalanceService.checkOpeningContinuity(period);
        if (Boolean.FALSE.equals(continuity.get("passed"))) {
            out.add(diagnostic("R_OPENING_DISCONTINUITY", "期初余额与上期期末不连续",
                    continuity.get("mismatchCount") + " 个科目本期期初与上期期末不符，最大差额 "
                            + continuity.get("maxAbsDiff") + "；结账前须核对期初建账或补齐衔接凭证"));
        }

        // REQ-037：存货占比过高预警（存货=1401/1403/1405/1406/1408 等 14xx 存货类）
        Map<String, Object> bs = balanceSheet(period);
        BigDecimal totalAssets = toBigDecimal(bs.get("totalAssets"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assets = (List<Map<String, Object>>) bs.get("assets");
        BigDecimal inventory = BigDecimal.ZERO;
        BigDecimal receivable = BigDecimal.ZERO;
        if (assets != null) {
            for (Map<String, Object> a : assets) {
                String code = String.valueOf(a.get("code"));
                BigDecimal bal = toBigDecimal(a.get("end_balance"));
                if (code.startsWith("14")) inventory = inventory.add(bal);
                if (code.startsWith("1122")) receivable = receivable.add(bal);
            }
        }
        if (totalAssets.signum() > 0 && inventory.compareTo(totalAssets.multiply(new BigDecimal("0.3"))) > 0) {
            BigDecimal ratio = inventory.multiply(new BigDecimal("100")).divide(totalAssets, 1, java.math.RoundingMode.HALF_UP);
            out.add(diagnostic("R_INVENTORY_HIGH", "存货占资产比例过高",
                    "存货 " + inventory + " 占总资产 " + totalAssets + " 的 " + ratio + "%，超过 30% 阈值，关注积压与跌价风险"));
        }
        if (totalAssets.signum() > 0 && receivable.compareTo(totalAssets.multiply(new BigDecimal("0.25"))) > 0) {
            BigDecimal ratio = receivable.multiply(new BigDecimal("100")).divide(totalAssets, 1, java.math.RoundingMode.HALF_UP);
            out.add(diagnostic("R_RECEIVABLE_HIGH", "应收账款占资产比例过高",
                    "应收账款 " + receivable + " 占总资产 " + totalAssets + " 的 " + ratio + "%，超过 25% 阈值，关注回款与坏账风险"));
        }

        return out;
    }

    private static Map<String, Object> diagnostic(String ruleId, String title, String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ruleId", ruleId);
        m.put("severity", "warning");
        m.put("title", title);
        m.put("detail", detail);
        return m;
    }

    @Override
    public List<Map<String, Object>> auxiliaryDetail(String period) {
        return reportDataMapper.auxiliaryMovement(period);
    }

    @Override
    public Map<String, Object> balanceSheet(String period) {
        return buildBalanceSheetWith(period, false);
    }

    @Override
    public Map<String, Object> balanceSheetWithReclassification(String period) {
        return buildBalanceSheetWith(period, true);
    }

    private Map<String, Object> buildBalanceSheetWith(String period, boolean reclassify) {
        Map<String, Object> result = buildBalanceSheet(period, END_BALANCE, reclassify);
        result.put("reclassifyEnabled", reclassify);
        Map<String, Object> yearStart = buildBalanceSheet(yearStartOf(period), BEGIN_BALANCE, reclassify);
        BigDecimal yearStartDiff = toBigDecimal(yearStart.get("diff"));
        result.put("yearStart", yearStart);
        result.put("yearStartCheckDiff", yearStartDiff);
        result.put("yearStartCheckOk", yearStartDiff.abs().compareTo(TOLERANCE) < 0);
        return result;
    }

    private static String yearStartOf(String period) {
        return period.substring(0, 4) + "01";
    }

    /**
     * P94 REQ-090：年初列与期末列的唯一差别是聚合基数，分类逻辑必须完全共用。
     * 年初 = 年初期间(1月)的期初余额；若小计仍按 1 月期末聚合，1 月有发生额时
     * 会出现"明细年初加总 ≠ 年初小计"，且与科目余额表期初对不上。
     */
    private Map<String, Object> buildBalanceSheet(String period, String balanceField) {
        return buildBalanceSheet(period, balanceField, false);
    }

    private Map<String, Object> buildBalanceSheet(String period, String balanceField, boolean reclassify) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> balances = reportDataMapper.subjectBalance(period);

        List<Map<String, Object>> assets = new ArrayList<>();
        List<Map<String, Object>> liab = new ArrayList<>();
        List<Map<String, Object>> equity = new ArrayList<>();
        List<Map<String, Object>> unbalancedItems = new ArrayList<>();
        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalLiab = BigDecimal.ZERO;
        BigDecimal totalEquityExProfit = BigDecimal.ZERO;
        BigDecimal costInInventory = BigDecimal.ZERO;
        BigDecimal profit4103 = BigDecimal.ZERO;
        BigDecimal profit4104 = BigDecimal.ZERO;
        BigDecimal currentPeriodProfit = BigDecimal.ZERO;
        // P92-B: 流动/非流动/其他三分小计。"其他"是合法报送分类（如 2701 专项储备按准则属权益
        // 但科目编号以 2 开头），必须参与小计，否则小计 ≠ 总计（P92B-BD2 勾稽）。
        BigDecimal currentAssets = BigDecimal.ZERO;
        BigDecimal nonCurrentAssets = BigDecimal.ZERO;
        BigDecimal otherAssets = BigDecimal.ZERO;
        BigDecimal currentLiabilities = BigDecimal.ZERO;
        BigDecimal nonCurrentLiabilities = BigDecimal.ZERO;
        BigDecimal otherLiabilities = BigDecimal.ZERO;

        for (Map<String, Object> row : balances) {
            String code = String.valueOf(row.get("code"));
            if (code == null || code.equals("null")) {
                continue;
            }
            String direction = (String) row.get("direction");
            BigDecimal balance = toBigDecimal(row.get(balanceField));
            char top = code.charAt(0);

            if (top >= '1' && top <= '6' && (direction == null || direction.isBlank())) {
                unbalancedItems.add(unclassified(code, row.get("name"), balance, "missing-direction"));
                continue;
            }

            switch (top) {
                case '1' -> {
                    BigDecimal signed = "debit".equals(direction) ? balance : balance.negate();
                    if (reclassify && signed.signum() < 0) {
                        // P97/REQ-098：预付/应收的贷方余额实质是预收，按准则重分类为负债列报。
                        // 纯列报层动作：不改账、不出凭证，只改这行落在哪一列。
                        reclassifyToLiability(row, signed, balanceField, liab);
                        totalLiab = totalLiab.add(signed.negate());
                    } else {
                        assets.add(row);
                        totalAssets = totalAssets.add(signed);
                    }
                }
                case '5' -> {
                    BigDecimal signed = "debit".equals(direction) ? balance : balance.negate();
                    assets.add(row);
                    totalAssets = totalAssets.add(signed);
                    costInInventory = costInInventory.add(signed);
                }
                case '2' -> {
                    BigDecimal signed = "credit".equals(direction) ? balance : balance.negate();
                    liab.add(row);
                    totalLiab = totalLiab.add(signed);
                }
                case '3' -> {
                    BigDecimal signed = "debit".equals(direction) ? balance : balance.negate();
                    if (signed.signum() >= 0) {
                        assets.add(row);
                        totalAssets = totalAssets.add(signed);
                    } else {
                        liab.add(row);
                        totalLiab = totalLiab.add(signed.negate());
                    }
                }
                case '4' -> {
                    BigDecimal signed = "credit".equals(direction) ? balance : balance.negate();
                    if (code.equals("4103")) {
                        profit4103 = signed;
                    } else if (code.equals("4104")) {
                        profit4104 = signed;
                    } else {
                        equity.add(row);
                        totalEquityExProfit = totalEquityExProfit.add(signed);
                    }
                }
                case '6' -> {
                    // 年初口径无"本期"概念：6xx 的发生额属当期损益，计入年初未分配利润会重复。
                    if (END_BALANCE.equals(balanceField)) {
                        BigDecimal currentNet = toBigDecimal(row.get("credit_total"))
                                .subtract(toBigDecimal(row.get("debit_total")));
                        currentPeriodProfit = currentPeriodProfit.add(currentNet);
                    }
                }
                default -> unbalancedItems.add(
                        unclassified(code, row.get("name"), balance, "unclassified"));
            }
        }

        // P92-B: 流动/非流动/其他三分小计（口径：三分 + 科目表 account_type 维护 + 小计行不折叠）
        // 不修改上方 switch 的主分类逻辑——小计是对已分类行的二次汇总，零侵入。
        // 取值优先 account_type，缺失时按科目段兜底（P92B-BD3：未分类不得静默丢弃）。
        // 注意 5x 成本科目也计入资产总计（小企业会计准则下存货类）：
        //   50 生产成本 / 51 制造费用 / 52 劳务成本 / 54 工程施工 → 流动（存货）
        //   53 研发支出 → 非流动（资本化开发支出资本性支出）
        // 14 段整体归流动：本套科目表 1408 是"委托加工物资"（属存货），
        // 不是通用准则的"持有待售资产"。不得对 1408 单列非流动——
        // 否则 1408 会被流动(14)与非流动(1408)两个 fallback 同时匹配，重复计入小计。
        currentAssets = subtotal(assets, "CURRENT_ASSET", CURRENT_ASSET_CODE, balanceField);
        nonCurrentAssets = subtotal(assets, "NON_CURRENT_ASSET", NON_CURRENT_ASSET_CODE, balanceField);
        otherAssets = totalAssets.subtract(currentAssets).subtract(nonCurrentAssets);

        currentLiabilities = subtotal(liab, "CURRENT_LIABILITY", CURRENT_LIABILITY_CODE, balanceField);
        nonCurrentLiabilities = subtotal(liab, "NON_CURRENT_LIABILITY", NON_CURRENT_LIABILITY_CODE, balanceField);
        otherLiabilities = totalLiab.subtract(currentLiabilities).subtract(nonCurrentLiabilities);
        // P94：逐行下发所属小计，前端据此做悬空保护（否则只能在前端复制一份科目段口径）
        tagSubtotalGroup(assets, "CURRENT_ASSET", CURRENT_ASSET_CODE);
        tagSubtotalGroup(assets, "NON_CURRENT_ASSET", NON_CURRENT_ASSET_CODE);
        tagSubtotalGroup(liab, "CURRENT_LIABILITY", CURRENT_LIABILITY_CODE);
        tagSubtotalGroup(liab, "NON_CURRENT_LIABILITY", NON_CURRENT_LIABILITY_CODE);
        for (Map<String, Object> row : assets) {
            row.putIfAbsent("subtotalGroup", "OTHER_ASSET");
        }
        for (Map<String, Object> row : liab) {
            row.putIfAbsent("subtotalGroup", "OTHER_LIABILITY");
        }
        // 勾稽（P92B-BD2）：各小计之和必须等于总计。otherAssets/otherLiabilities 由减法反推得出，
        // 因此上述等式恒成立；这里显式校验而非依赖恒等式，防止未来改动 switch 分类后小计漏项。
        if (currentAssets.add(nonCurrentAssets).add(otherAssets).compareTo(totalAssets) != 0
                || currentLiabilities.add(nonCurrentLiabilities).add(otherLiabilities).compareTo(totalLiab) != 0) {
            throw new IllegalStateException("资产负债表流动分类小计与总计不一致");
        }

        BigDecimal currentYearProfit = profit4103.add(profit4104).add(currentPeriodProfit);
        BigDecimal totalEquity = totalEquityExProfit.add(currentYearProfit);
        BigDecimal totalLiabEquity = totalLiab.add(totalEquity);
        BigDecimal diff = totalAssets.subtract(totalLiabEquity).setScale(2, RoundingMode.HALF_UP);
        boolean balanced = diff.abs().compareTo(new BigDecimal("0.01")) < 0
                && unbalancedItems.isEmpty();

        // P88①: 未分配利润(4103 本年利润 + 4104 利润分配-未分配利润 + 当期未结转 6xx 净额)
        // 必须以显式行出现在权益区，否则前端逐行加总 ≠ 权益合计
        // （旧版只算进 totalEquity，用户肉眼对不上差额）。
        // 0 值时不追加（报表惯例：未分配利润 0 不占行，避免噪音）。
        if (currentYearProfit.signum() != 0) {
            Map<String, Object> cypRow = new LinkedHashMap<>();
            cypRow.put("code", "4103");
            cypRow.put("name", "未分配利润");
            cypRow.put("direction", currentYearProfit.signum() < 0 ? "debit" : "credit");
            cypRow.put(BEGIN_BALANCE, BigDecimal.ZERO);
            cypRow.put(END_BALANCE, BigDecimal.ZERO);
            cypRow.put(balanceField, currentYearProfit);
            cypRow.put("rowType", "currentYearProfit");
            equity.add(cypRow);
        }

        result.put("period", period);
        result.put("assets", assets);
        result.put("liabilities", liab);
        result.put("equity", equity);
        // P92-B: 三分小计作为独立字段返回，不混入 assets/liabilities 数组。
        // 两个原因：① 不折叠口径要求小计行常驻，独立字段天然不受 hideZeroRows 影响；
        //          ② 混入行数组会让前端逐行加总把小计也算进总计，重复计入。
        // P92B-BD2 勾稽由下方内联校验兜住（小计之和 ≠ 总计则抛 IllegalStateException）。
        result.put("currentAssets", money(currentAssets));
        result.put("nonCurrentAssets", money(nonCurrentAssets));
        result.put("otherAssets", money(otherAssets));
        result.put("currentLiabilities", money(currentLiabilities));
        result.put("nonCurrentLiabilities", money(nonCurrentLiabilities));
        result.put("otherLiabilities", money(otherLiabilities));
        result.put("currentYearProfit", money(currentYearProfit));
        result.put("costInInventory", money(costInInventory));
        result.put("totalAssets", money(totalAssets));
        result.put("totalLiabilities", money(totalLiab));
        result.put("totalEquity", money(totalEquity));
        result.put("totalLiabEquity", money(totalLiabEquity));
        result.put("diff", diff);
        result.put("unbalancedItems", unbalancedItems);
        result.put("balanced", balanced);
        return result;
    }

    private static Map<String, Object> unclassified(String code, Object name,
                                                     BigDecimal endBalance, String reason) {
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("code", code);
        bad.put("name", name);
        bad.put("endBalance", endBalance);
        bad.put("classifiedTo", reason);
        return bad;
    }

    /**
     * 把资产类的负（贷方）余额行改列为负债，并把展示金额取绝对值。
     * 同时打 reclassified 标记，前端可据此提示"该行由重分类而来"，避免看起来像数据错误。
     */
    private static void reclassifyToLiability(Map<String, Object> row, BigDecimal signed,
                                              String balanceField, List<Map<String, Object>> liab) {
        row.put(END_BALANCE, signed.negate());
        row.put(BEGIN_BALANCE, BigDecimal.ZERO);
        row.put(balanceField, signed.negate());
        row.put("reclassified", Boolean.TRUE);
        liab.add(row);
    }

    /** 命中判定与 subtotal() 完全一致（account_type 优先，科目段兜底），保证标记与小计同源。 */
    private static void tagSubtotalGroup(List<Map<String, Object>> rows, String target,
                                          java.util.function.Predicate<String> fallback) {
        for (Map<String, Object> row : rows) {
            Object at = row.get("account_type");
            String accountType = (at == null) ? null : String.valueOf(at);
            boolean byType = target.equals(accountType);
            boolean byFallback = !byType
                    && (accountType == null || accountType.isBlank() || "null".equals(accountType))
                    && fallback.test(String.valueOf(row.get("code")));
            if (byType || byFallback) {
                row.put("subtotalGroup", target);
            }
        }
    }

    /**
     * P92-B: 对已分类的资产/负债行按 account_type 求小计。
     *
     * <p>命中条件：行的 account_type 等于 target，或 account_type 缺失/为空且 fallback 判定为真
     * （科目段兜底，保证迁移前数据与人工漏填时不静默丢弃，见 P92B-BD3）。
     *
     * <p>金额口径必须与 balanceSheet 主循环的 signed 一致：
     * 资产(1x/5x)借方为正、负债(2x)贷方为正。因此不能直接取余额（其符号依赖方向），
     * 必须按行上的 direction 重新定向，否则小计会与总计符号相反。
     *
     * <p>balanceField：P94 起小计随主循环取同一余额字段，保证同一张表内年初/期末不混口径。
     */
    private static BigDecimal subtotal(List<Map<String, Object>> rows, String target,
                                       java.util.function.Predicate<String> fallback,
                                       String balanceField) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            String code = String.valueOf(row.get("code"));
            String direction = (String) row.get("direction");
            Object at = row.get("account_type");
            String accountType = (at == null) ? null : String.valueOf(at);
            boolean byType = target.equals(accountType);
            boolean byFallback = !byType && (accountType == null || accountType.isBlank()
                    || "null".equals(accountType))
                    && fallback.test(code);
            if (!byType && !byFallback) {
                continue;
            }
            BigDecimal balance = toBigDecimal(row.get(balanceField));
            BigDecimal signed = code.startsWith("2")
                    ? ("credit".equals(direction) ? balance : balance.negate())
                    : ("debit".equals(direction) ? balance : balance.negate());
            sum = sum.add(signed);
        }
        return sum;
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public Map<String, Object> incomeStatement(String period) {
        Map<String, Object> data = reportDataMapper.incomeStatementData(period);
        String yearStart = period.substring(0, 4) + "01";
        Map<String, Object> cumulative = reportDataMapper.cumulativeData(yearStart, period);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", period);
        // 本期与累计逐段搬运，口径完全一致：段位由 SQL 显式枚举，此处只做链式汇总
        putSegments(result, "", data);
        putSegments(result, "cumulative", cumulative);
        result.put("grossProfit", n(result, "revenue").subtract(n(result, "cost")));
        result.put("cumulativeGrossProfit", n(result, "cumulativeRevenue").subtract(n(result, "cumulativeCost")));
        result.put("operatingProfit", operatingProfit(result, ""));
        result.put("cumulativeOperatingProfit", operatingProfit(result, "cumulative"));
        result.put("totalProfit", totalProfit(result, ""));
        result.put("cumulativeProfit", totalProfit(result, "cumulative"));
        result.put("netProfit", n(result, "totalProfit").subtract(n(result, "incomeTax")));
        result.put("cumulativeNetProfit",
                n(result, "cumulativeProfit").subtract(n(result, "cumulativeIncomeTax")));
        return result;
    }

    /** 段位字段清单：SQL 侧别名与此处一一对应，缺一个即漏一行报表。 */
    private static final List<String> INCOME_SEGMENTS = List.of(
            "revenue", "cost", "taxAndSurcharge",
            "sellingExpense", "adminExpense", "financialExpense", "rdExpense",
            "otherIncome", "investmentIncome", "fairValueIncome", "assetDisposalIncome",
            "assetImpairmentLoss",
            "nonOperatingIncome", "nonOperatingExpense", "incomeTax");

    private static void putSegments(Map<String, Object> result, String prefix, Map<String, Object> row) {
        for (String seg : INCOME_SEGMENTS) {
            // 累计列的 camel 键与 SQL snake 键前缀不同（cumulativeRevenue ↔ cumulative_revenue），
            // 必须由同一个 camelKey 派生，写键与读键才不会错位
            String camelKey = key(prefix, seg);
            result.put(camelKey, toBigDecimal(getOrNull(row, toSnake(camelKey))));
        }
    }

    /**
     * 营业利润 = 营业收入 − 营业成本 − 税金及附加 − 期间费用
     *           + 其他收益 + 投资收益 + 公允价值变动收益 + 资产处置收益 − 资产减值损失。
     * 期间费用 = 销售 + 管理 + 财务 + 研发（研发独立成行，不得再混进「其他支出」）。
     */
    private static BigDecimal operatingProfit(Map<String, Object> r, String p) {
        BigDecimal periodExpense = n(r, key(p, "sellingExpense"))
                .add(n(r, key(p, "adminExpense")))
                .add(n(r, key(p, "financialExpense")))
                .add(n(r, key(p, "rdExpense")));
        r.put(key(p, "periodExpense"), periodExpense);
        return n(r, key(p, "revenue"))
                .subtract(n(r, key(p, "cost")))
                .subtract(n(r, key(p, "taxAndSurcharge")))
                .subtract(periodExpense)
                .add(n(r, key(p, "otherIncome")))
                .add(n(r, key(p, "investmentIncome")))
                .add(n(r, key(p, "fairValueIncome")))
                .add(n(r, key(p, "assetDisposalIncome")))
                .subtract(n(r, key(p, "assetImpairmentLoss")));
    }

    /** 利润总额 = 营业利润 + 营业外收入 − 营业外支出 */
    private static BigDecimal totalProfit(Map<String, Object> r, String p) {
        return n(r, key(p, "operatingProfit"))
                .add(n(r, key(p, "nonOperatingIncome")))
                .subtract(n(r, key(p, "nonOperatingExpense")));
    }

    /**
     * 结果键拼法：累计列为 {@code cumulativeRevenue}（驼峰），若用 {@code prefix + "revenue"} 会拼出
     * {@code cumulativerevenue} 而读不到值——写键与读键必须走同一个函数，否则整条累计链静默归零。
     */
    private static String key(String prefix, String seg) {
        return prefix.isEmpty() ? seg
                : prefix + Character.toUpperCase(seg.charAt(0)) + seg.substring(1);
    }

    private static BigDecimal n(Map<String, Object> r, String key) {
        return toBigDecimal(r.get(key));
    }

    private static String toSnake(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    private static Object getOrNull(Map<String, Object> map, String key) {
        return map == null ? null : map.get(key);
    }

    @Override
    public Map<String, Object> cashFlowStatement(String period) {
        // P92-A：本年累计 = 年初期间(1月) 至查询期间。
        // flow_type 判定逻辑只存在于 cashFlowData 的 SQL 一处，mapper 改为期间范围参数调两次，
        // 不复制 SQL——否则两处判定会各自演化导致取数口径漂移。
        String yearStart = period.substring(0, 4) + "01";
        FlowSums cur = FlowSums.aggregate(reportDataMapper.cashFlowData(period, period));
        FlowSums ytd = FlowSums.aggregate(reportDataMapper.cashFlowData(yearStart, period));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", period);
        result.put("operatingIn", cur.opIn());
        result.put("operatingOut", cur.opOut());
        result.put("operatingNet", cur.opNet());
        result.put("investingIn", cur.invIn());
        result.put("investingOut", cur.invOut());
        result.put("investingNet", cur.invNet());
        result.put("financingIn", cur.finIn());
        result.put("financingOut", cur.finOut());
        result.put("financingNet", cur.finNet());
        result.put("totalNet", cur.totalNet());
        result.put("operatingInYtd", ytd.opIn());
        result.put("operatingOutYtd", ytd.opOut());
        result.put("operatingNetYtd", ytd.opNet());
        result.put("investingInYtd", ytd.invIn());
        result.put("investingOutYtd", ytd.invOut());
        result.put("investingNetYtd", ytd.invNet());
        result.put("financingInYtd", ytd.finIn());
        result.put("financingOutYtd", ytd.finOut());
        result.put("financingNetYtd", ytd.finNet());
        result.put("totalNetYtd", ytd.totalNet());

        // P88③：补"期初/期末现金"闭环——期初取 1001+1002 本期期初余额；
        // 期末 = 期初 + 现金净流量；并与 1001+1002 本期期末余额勾稽（差异提示，不阻断）
        Map<String, Object> cash = reportDataMapper.cashSubjectBalance(period);
        BigDecimal openingCash = toBigDecimal(getOrNull(cash, "begin_cash"));
        BigDecimal endBalanceFromSubject = toBigDecimal(getOrNull(cash, "end_cash"));
        BigDecimal closingCash = openingCash.add(cur.totalNet());
        BigDecimal cashCheckDiff = endBalanceFromSubject.subtract(closingCash);
        result.put("openingCash", openingCash);
        result.put("closingCash", closingCash);
        result.put("endBalanceFromSubject", endBalanceFromSubject);
        result.put("cashCheckDiff", cashCheckDiff);
        result.put("cashCheckOk", cashCheckDiff.abs().compareTo(new BigDecimal("0.01")) < 0);

        // P92-A：本年累计期初现金 = 年初期间(1月)的期初余额，即 1 月 1 日现金余额。
        // 若误用查询期间的 begin_cash，跨年时累计期初会变成当月月初，累计列漏计年初至当月的现金。
        // 期末现金与本期期末余额勾稽（同一时点，应一致）。
        Map<String, Object> cashYearStart = reportDataMapper.cashSubjectBalance(yearStart);
        BigDecimal openingCashYtd = toBigDecimal(getOrNull(cashYearStart, "begin_cash"));
        BigDecimal closingCashYtd = openingCashYtd.add(ytd.totalNet());
        BigDecimal cashCheckDiffYtd = endBalanceFromSubject.subtract(closingCashYtd);
        result.put("openingCashYtd", openingCashYtd);
        result.put("closingCashYtd", closingCashYtd);
        result.put("cashCheckDiffYtd", cashCheckDiffYtd);
        result.put("cashCheckOkYtd", cashCheckDiffYtd.abs().compareTo(new BigDecimal("0.01")) < 0);

        // P96 REQ-096：间接法补充资料（将净利润调节为经营活动现金流量净额）
        // 公式：净利润 + 非付现费用(折旧/摊销) + 财务费用 - 经营性应收增加 + 经营性应付增加 - 存货增加
        Map<String, Object> adj = reportDataMapper.indirectMethodAdjustments(period);
        BigDecimal nonCashExpense = toBigDecimal(getOrNull(adj, "non_cash_expense"));
        BigDecimal financialExpense = toBigDecimal(getOrNull(adj, "financial_expense"));
        BigDecimal arChange = toBigDecimal(getOrNull(adj, "ar_change"));
        BigDecimal apChange = toBigDecimal(getOrNull(adj, "ap_change"));
        BigDecimal inventoryChange = toBigDecimal(getOrNull(adj, "inventory_change"));
        // 净利润从利润表取
        Map<String, Object> income = incomeStatement(period);
        BigDecimal netProfit = toBigDecimal(income.get("netProfit"));
        BigDecimal indirectOperatingNet = netProfit
                .add(nonCashExpense)
                .add(financialExpense)
                .subtract(arChange)
                .add(apChange)
                .subtract(inventoryChange);
        BigDecimal indirectCheckDiff = indirectOperatingNet.subtract(cur.opNet());
        List<Map<String, Object>> supplement = new ArrayList<>();
        supplement.add(Map.of("label", "净利润", "amount", netProfit));
        supplement.add(Map.of("label", "加：固定资产折旧、油气资产折耗、生产性生物资产折旧", "amount", nonCashExpense));
        supplement.add(Map.of("label", "    无形资产摊销", "amount", BigDecimal.ZERO));
        supplement.add(Map.of("label", "    长期待摊费用摊销", "amount", BigDecimal.ZERO));
        supplement.add(Map.of("label", "财务费用（收益以\"－\"号填列）", "amount", financialExpense));
        supplement.add(Map.of("label", "经营性应收项目的减少（增加以\"－\"号填列）", "amount", arChange.negate()));
        supplement.add(Map.of("label", "经营性应付项目的增加（减少以\"－\"号填列）", "amount", apChange));
        supplement.add(Map.of("label", "存货的减少（增加以\"－\"号填列）", "amount", inventoryChange.negate()));
        supplement.add(Map.of("label", "经营活动产生的现金流量净额", "amount", indirectOperatingNet, "bold", true));
        result.put("supplement", supplement);
        result.put("indirectOperatingNet", indirectOperatingNet);
        result.put("indirectCheckDiff", indirectCheckDiff);
        result.put("indirectCheckOk", indirectCheckDiff.abs().compareTo(new BigDecimal("0.01")) < 0);

        return result;
    }

    /**
     * P92-A：现金流量表三类活动的流入/流出/净额聚合。
     * 本期与本年累计共用同一实现，保证两路口径完全一致。
     */
    private record FlowSums(BigDecimal opIn, BigDecimal opOut, BigDecimal invIn, BigDecimal invOut,
                            BigDecimal finIn, BigDecimal finOut,
                            BigDecimal opNet, BigDecimal invNet, BigDecimal finNet, BigDecimal totalNet) {
        static FlowSums aggregate(List<Map<String, Object>> rows) {
            BigDecimal opIn = BigDecimal.ZERO, opOut = BigDecimal.ZERO;
            BigDecimal invIn = BigDecimal.ZERO, invOut = BigDecimal.ZERO;
            BigDecimal finIn = BigDecimal.ZERO, finOut = BigDecimal.ZERO;
            for (Map<String, Object> row : rows) {
                String type = (String) row.get("flow_type");
                BigDecimal amount = toBigDecimal(row.get("amount"));
                switch (type) {
                    case "OPERATING_IN":  opIn  = opIn.add(amount); break;
                    case "OPERATING_OUT": opOut = opOut.add(amount); break;
                    case "INVESTING_IN":  invIn  = invIn.add(amount); break;
                    case "INVESTING_OUT": invOut = invOut.add(amount); break;
                    case "FINANCING_IN":  finIn  = finIn.add(amount); break;
                    case "FINANCING_OUT": finOut = finOut.add(amount); break;
                }
            }
            BigDecimal opNet = opIn.subtract(opOut);
            BigDecimal invNet = invIn.subtract(invOut);
            BigDecimal finNet = finIn.subtract(finOut);
            return new FlowSums(opIn, opOut, invIn, invOut, finIn, finOut,
                    opNet, invNet, finNet, opNet.add(invNet).add(finNet));
        }
    }

    @Override
    public List<Map<String, Object>> trend(String startPeriod, String endPeriod) {
        return reportDataMapper.trendData(startPeriod, endPeriod);
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        return new BigDecimal(o.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private void writeExcel(HttpServletResponse response, String title, String period, String[] headers, List<List<Object>> rows) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String fileName = title + "_" + period;
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + ".xlsx");
        ExcelWriter writer = ExcelUtil.getWriter(true);
        writer.addHeaderAlias("col", title);

        // P89-D：导出抬头（报表标题 + 期间 + 制表人 + 制表日期）
        int cols = headers.length;
        writer.merge(0, 0, 0, cols - 1, title, false);

        String operator = "未知";
        try {
            operator = SecurityUtils.getCurrentUsername();
        } catch (Exception ignored) {
            // 未登录上下文（如后台任务/测试）不阻断导出
        }
        // 审核人：PRD-018 §4.2 指定"留白"——报表未建立强制审核流，此处占位提示待审核，
        // 不伪造审核人，避免导出件被误认为已审。
        writer.writeCellValue(0, 1, "期间：" + period);
        // 制表人信息合并到最后一列；现金流量表仅 3 列，merge(2,2) 是单格合并，
        // POI 会抛 "Merged region must contain 2 or more cells"，故列数 < 4 时退化为不合并
        String metaInfo = "制表人：" + operator + "　制表日期：" + LocalDate.now() + "　审核人：待审核";
        if (cols >= 4) {
            writer.merge(1, 1, 2, cols - 1, metaInfo, false);
        } else {
            writer.writeCellValue(2, 1, metaInfo);
        }

        int headerRow = 2;
        for (int i = 0; i < cols; i++) {
            writer.writeCellValue(i, headerRow, headers[i]);
        }
        for (int i = 0; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            for (int j = 0; j < row.size(); j++) {
                writer.writeCellValue(j, headerRow + 1 + i, row.get(j));
            }
        }
        writer.flush(response.getOutputStream());
        writer.close();
    }

    @Override
    public void exportSubjectBalance(String period, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> data = subjectBalanceTable(period);
        String[] headers = {"科目编码", "科目名称", "余额方向", "期初余额", "本期借方", "本期贷方", "期末余额"};
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : data) {
            rows.add(List.of(
                row.get("code"), row.get("name"), directionLabel(row.get("direction")),
                row.get("begin_balance"), row.get("debit_total"),
                row.get("credit_total"), row.get("end_balance")
            ));
        }
        writeExcel(response, "科目余额表", period, headers, rows);
    }

    /** 科目记账方向转中文；空值返回 "—"，与前端科目余额表方向列保持一致 */
    private static String directionLabel(Object direction) {
        if (direction == null) return "—";
        String d = direction.toString();
        if ("credit".equals(d)) return "贷";
        if ("debit".equals(d)) return "借";
        return "—";
    }

    @Override
    public void exportBalanceSheet(String period, HttpServletResponse response) throws IOException {
        Map<String, Object> data = balanceSheet(period);
        // P94 REQ-090：年初列小计直接取 balanceSheet 已算好的 yearStart 区块（begin 口径聚合）。
        // 不可改回 balanceSheet(YYYY01)——那是 1 月期末口径，与明细年初值不同源。
        @SuppressWarnings("unchecked")
        Map<String, Object> ysData = (Map<String, Object>) data.get("yearStart");
        String[] headers = {"项目", "行次", "期末余额", "年初余额"};
        List<List<Object>> rows = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assets = (List<Map<String, Object>>) data.get("assets");
        for (Map<String, Object> a : assets) {
            rows.add(List.of(a.get("name"), "", a.get("end_balance"), a.get("begin_balance")));
        }
        // P92-B: 三分小计行，导出与前端一致（法定报送口径，不可省略）
        rows.add(List.of("流动资产合计", "", data.get("currentAssets"),
                ysData == null ? "" : ysData.get("currentAssets")));
        rows.add(List.of("非流动资产合计", "", data.get("nonCurrentAssets"),
                ysData == null ? "" : ysData.get("nonCurrentAssets")));
        rows.add(List.of("其他资产", "", data.get("otherAssets"),
                ysData == null ? "" : ysData.get("otherAssets")));
        rows.add(List.of("资产总计", "", data.get("totalAssets"),
                ysData == null ? "" : ysData.get("totalAssets")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> liab = (List<Map<String, Object>>) data.get("liabilities");
        for (Map<String, Object> l : liab) {
            rows.add(List.of(l.get("name"), "", l.get("end_balance"), l.get("begin_balance")));
        }
        // P92-B: 负债三分小计
        rows.add(List.of("流动负债合计", "", data.get("currentLiabilities"),
                ysData == null ? "" : ysData.get("currentLiabilities")));
        rows.add(List.of("非流动负债合计", "", data.get("nonCurrentLiabilities"),
                ysData == null ? "" : ysData.get("nonCurrentLiabilities")));
        rows.add(List.of("其他负债", "", data.get("otherLiabilities"),
                ysData == null ? "" : ysData.get("otherLiabilities")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> equity = (List<Map<String, Object>>) data.get("equity");
        // P88①：权益区含"未分配利润"显式行，与前端逐行加总口径一致
        for (Map<String, Object> e : equity) {
            rows.add(List.of(e.get("name"), "", e.get("end_balance"), e.get("begin_balance")));
        }
        rows.add(List.of("负债+所有者权益合计", "", data.get("totalLiabEquity"),
                ysData == null ? "" : ysData.get("totalLiabEquity")));
        writeExcel(response, "资产负债表", period, headers, rows);
    }

    @Override
    public void exportIncomeStatement(String period, HttpServletResponse response) throws IOException {
        Map<String, Object> data = incomeStatement(period);
        String[] headers = {"项目", "行次", "本期金额", "本年累计"};
        List<List<Object>> rows = new ArrayList<>();
        // P97/REQ-099：按企业会计准则法定行序输出，行次连续；
        // 各行本期/累计逐行对齐，取值一律走 incomeStatement 返回的段位键。
        rows.add(row("一、营业收入", "1", data, "revenue"));
        rows.add(row("减：营业成本", "2", data, "cost"));
        rows.add(row("减：税金及附加", "3", data, "taxAndSurcharge"));
        rows.add(row("减：销售费用", "4", data, "sellingExpense"));
        rows.add(row("减：管理费用", "5", data, "adminExpense"));
        rows.add(row("减：研发费用", "6", data, "rdExpense"));
        rows.add(row("减：财务费用", "7", data, "financialExpense"));
        rows.add(row("加：其他收益", "8", data, "otherIncome"));
        rows.add(row("加：投资收益", "9", data, "investmentIncome"));
        rows.add(row("加：公允价值变动收益", "10", data, "fairValueIncome"));
        rows.add(row("加：资产处置收益", "11", data, "assetDisposalIncome"));
        rows.add(row("减：资产减值损失", "12", data, "assetImpairmentLoss"));
        rows.add(row("三、营业利润", "13", data, "operatingProfit"));
        rows.add(row("加：营业外收入", "14", data, "nonOperatingIncome"));
        rows.add(row("减：营业外支出", "15", data, "nonOperatingExpense"));
        rows.add(row("四、利润总额", "16", data, "totalProfit"));
        rows.add(row("减：所得税费用", "17", data, "incomeTax"));
        rows.add(row("五、净利润", "18", data, "netProfit"));
        writeExcel(response, "利润表", period, headers, rows);
    }

    /** 导出行：本期金额 + 本年累计，缺值补空串避免 List.of 遇 null 抛 NPE */
    private static List<Object> row(String label, String lineNo, Map<String, Object> data, String seg) {
        return List.of(label, lineNo,
                data.getOrDefault(seg, ""), data.getOrDefault("cumulative" + Character.toUpperCase(seg.charAt(0))
                + seg.substring(1), ""));
    }

    @Override
    public void exportCashFlow(String period, HttpServletResponse response) throws IOException {
        Map<String, Object> data = cashFlowStatement(period);
        String[] headers = {"项目", "行次", "本期金额", "本年累计金额"};
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("经营活动现金流入", "1", data.get("operatingIn"), data.get("operatingInYtd")));
        rows.add(List.of("经营活动现金流出", "2", data.get("operatingOut"), data.get("operatingOutYtd")));
        rows.add(List.of("经营活动净额", "3", data.get("operatingNet"), data.get("operatingNetYtd")));
        rows.add(List.of("投资活动现金流入", "4", data.get("investingIn"), data.get("investingInYtd")));
        rows.add(List.of("投资活动现金流出", "5", data.get("investingOut"), data.get("investingOutYtd")));
        rows.add(List.of("投资活动净额", "6", data.get("investingNet"), data.get("investingNetYtd")));
        rows.add(List.of("筹资活动现金流入", "7", data.get("financingIn"), data.get("financingInYtd")));
        rows.add(List.of("筹资活动现金流出", "8", data.get("financingOut"), data.get("financingOutYtd")));
        rows.add(List.of("筹资活动净额", "9", data.get("financingNet"), data.get("financingNetYtd")));
        rows.add(List.of("五、现金及现金等价物净增加额", "10", data.get("totalNet"), data.get("totalNetYtd")));
        // P88③：期初/期末现金 + 勾稽校验（与 1001+1002 期末余额比对）
        rows.add(List.of("加：期初现金及现金等价物余额", "11", data.get("openingCash"), data.get("openingCashYtd")));
        rows.add(List.of("六、期末现金及现金等价物余额", "12", data.get("closingCash"), data.get("closingCashYtd")));
        if (Boolean.TRUE.equals(data.get("cashCheckOk"))) {
            rows.add(List.of("勾稽校验", "13", "期末现金 = 期初 + 净流量，与 1001+1002+1009+1012 期末余额一致 ✓", ""));
        } else {
            rows.add(List.of("勾稽校验", "13", "⚠ 差异 " + data.get("cashCheckDiff")
                    + "（期末现金计算值 vs 科目余额 1001+1002+1009+1012）", ""));
        }
        writeExcel(response, "现金流量表", period, headers, rows);
    }

    @Override
    public List<Map<String, Object>> customSubjectBalance(String period, Integer level, String codePrefix, Boolean onlyWithBalance) {
        List<Map<String, Object>> data = subjectBalanceTable(period);
        return data.stream()
                .filter(row -> {
                    if (level != null) {
                        Object lv = row.get("level");
                        if (lv == null || !level.toString().equals(lv.toString())) return false;
                    }
                    if (codePrefix != null && !codePrefix.isEmpty()) {
                        Object code = row.get("code");
                        if (code == null || !code.toString().startsWith(codePrefix)) return false;
                    }
                    if (Boolean.TRUE.equals(onlyWithBalance)) {
                        BigDecimal end = toBigDecimal(row.get("end_balance"));
                        BigDecimal begin = toBigDecimal(row.get("begin_balance"));
                        if (end.signum() == 0 && begin.signum() == 0) return false;
                    }
                    return true;
                })
                .toList();
    }

    @Override
    public void exportCustomSubjectBalance(String period, Integer level, String codePrefix, Boolean onlyWithBalance, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> data = customSubjectBalance(period, level, codePrefix, onlyWithBalance);
        String[] headers = {"科目编码", "科目名称", "级次", "余额方向", "期初余额", "本期借方", "本期贷方", "期末余额"};
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : data) {
            rows.add(List.of(
                    row.get("code"), row.get("name"), row.get("level"), directionLabel(row.get("direction")),
                    row.get("begin_balance"), row.get("debit_total"),
                    row.get("credit_total"), row.get("end_balance")
            ));
        }
        writeExcel(response, "自定义科目余额表", period, headers, rows);
    }
}
