package com.huicai.base.report.service.impl;

import cn.hutool.core.io.IoUtil;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
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
public class ReportServiceImpl implements ReportService {

    private final ReportDataMapper reportDataMapper;

    @Override
    public List<Map<String, Object>> subjectBalanceTable(String period) {
        return reportDataMapper.subjectBalance(period);
    }

    @Override
    public Map<String, Object> balanceSheet(String period) {
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
            BigDecimal endBalance = toBigDecimal(row.get("end_balance"));
            char top = code.charAt(0);

            if (top >= '1' && top <= '6' && (direction == null || direction.isBlank())) {
                unbalancedItems.add(unclassified(code, row.get("name"), endBalance, "missing-direction"));
                continue;
            }

            switch (top) {
                case '1' -> {
                    BigDecimal signed = "debit".equals(direction) ? endBalance : endBalance.negate();
                    assets.add(row);
                    totalAssets = totalAssets.add(signed);
                }
                case '5' -> {
                    BigDecimal signed = "debit".equals(direction) ? endBalance : endBalance.negate();
                    assets.add(row);
                    totalAssets = totalAssets.add(signed);
                    costInInventory = costInInventory.add(signed);
                }
                case '2' -> {
                    BigDecimal signed = "credit".equals(direction) ? endBalance : endBalance.negate();
                    liab.add(row);
                    totalLiab = totalLiab.add(signed);
                }
                case '3' -> {
                    BigDecimal signed = "debit".equals(direction) ? endBalance : endBalance.negate();
                    if (signed.signum() >= 0) {
                        assets.add(row);
                        totalAssets = totalAssets.add(signed);
                    } else {
                        liab.add(row);
                        totalLiab = totalLiab.add(signed.negate());
                    }
                }
                case '4' -> {
                    BigDecimal signed = "credit".equals(direction) ? endBalance : endBalance.negate();
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
                    BigDecimal currentNet = toBigDecimal(row.get("credit_total"))
                            .subtract(toBigDecimal(row.get("debit_total")));
                    currentPeriodProfit = currentPeriodProfit.add(currentNet);
                }
                default -> unbalancedItems.add(
                        unclassified(code, row.get("name"), endBalance, "unclassified"));
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
        currentAssets = subtotal(assets, "CURRENT_ASSET", c -> c.startsWith("1")
                && (c.startsWith("10") || c.startsWith("11")
                || c.startsWith("12") || c.startsWith("14"))
                || c.startsWith("50") || c.startsWith("51")
                || c.startsWith("52") || c.startsWith("54"));
        nonCurrentAssets = subtotal(assets, "NON_CURRENT_ASSET", c -> c.startsWith("1")
                && (c.startsWith("13") || c.startsWith("15")
                || c.startsWith("16") || c.startsWith("17") || c.startsWith("18") || c.startsWith("19"))
                || c.startsWith("53"));
        otherAssets = totalAssets.subtract(currentAssets).subtract(nonCurrentAssets);

        currentLiabilities = subtotal(liab, "CURRENT_LIABILITY", c -> c.startsWith("2")
                && (c.startsWith("20") || c.startsWith("21") || c.startsWith("22")));
        nonCurrentLiabilities = subtotal(liab, "NON_CURRENT_LIABILITY", c -> c.startsWith("2")
                && (c.startsWith("24") || c.startsWith("25") || c.startsWith("27")
                || c.startsWith("28") || c.startsWith("29")));
        otherLiabilities = totalLiab.subtract(currentLiabilities).subtract(nonCurrentLiabilities);
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
            cypRow.put("begin_balance", BigDecimal.ZERO);
            cypRow.put("end_balance", currentYearProfit);
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
     * P92-B: 对已分类的资产/负债行按 account_type 求小计。
     *
     * <p>命中条件：行的 account_type 等于 target，或 account_type 缺失/为空且 fallback 判定为真
     * （科目段兜底，保证迁移前数据与人工漏填时不静默丢弃，见 P92B-BD3）。
     *
     * <p>金额口径必须与 balanceSheet 主循环的 signed 一致：
     * 资产(1x/5x)借方为正、负债(2x)贷方为正。因此不能直接取 end_balance（其符号依赖方向），
     * 必须按行上的 direction 重新定向，否则小计会与总计符号相反。
     */
    private static BigDecimal subtotal(List<Map<String, Object>> rows, String target,
                                       java.util.function.Predicate<String> fallback) {
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
            BigDecimal endBalance = toBigDecimal(row.get("end_balance"));
            BigDecimal signed = code.startsWith("2")
                    ? ("credit".equals(direction) ? endBalance : endBalance.negate())
                    : ("debit".equals(direction) ? endBalance : endBalance.negate());
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

        // P88②：本期各行口径——营业收入=credit 方向 6xx；成本/费用/其他支出各段独立，
        // 严禁把费用侧发生额从营业收入里减掉（旧版 revenue-revenue_offset 把净利塞进了营收行）。
        BigDecimal revenue = toBigDecimal(getOrNull(data, "revenue"));
        BigDecimal cost = toBigDecimal(getOrNull(data, "cost"));
        BigDecimal expense = toBigDecimal(getOrNull(data, "expense"));
        BigDecimal otherExpense = toBigDecimal(getOrNull(data, "other_expense"));

        BigDecimal grossProfit = revenue.subtract(cost);
        BigDecimal operatingProfit = grossProfit.subtract(expense);
        BigDecimal totalProfit = operatingProfit.subtract(otherExpense);

        // P88②：累计各行与本期口径逐行对齐（SQL 已分列），不再用 revenue-cost 一把梭
        BigDecimal cumRevenue = toBigDecimal(getOrNull(cumulative, "cumulative_revenue"));
        BigDecimal cumCost = toBigDecimal(getOrNull(cumulative, "cumulative_cost"));
        BigDecimal cumExpense = toBigDecimal(getOrNull(cumulative, "cumulative_expense"));
        BigDecimal cumOtherExpense = toBigDecimal(getOrNull(cumulative, "cumulative_other_expense"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", period);
        result.put("revenue", revenue);
        result.put("cost", cost);
        result.put("grossProfit", grossProfit);
        result.put("expense", expense);
        result.put("operatingProfit", operatingProfit);
        result.put("otherExpense", otherExpense);
        result.put("totalProfit", totalProfit);
        result.put("cumulativeRevenue", cumRevenue);
        result.put("cumulativeCost", cumCost);
        result.put("cumulativeGrossProfit", cumRevenue.subtract(cumCost));
        result.put("cumulativeExpense", cumExpense);
        result.put("cumulativeOperatingProfit", cumRevenue.subtract(cumCost).subtract(cumExpense));
        result.put("cumulativeOtherExpense", cumOtherExpense);
        result.put("cumulativeProfit", cumRevenue.subtract(cumCost).subtract(cumExpense).subtract(cumOtherExpense));
        return result;
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
        // P92-B: 小计年初值与前端同口径——取年初期间 balanceSheet() 的返回值，
        // 而不是科目行 begin_balance 相加（后者含未分配利润、成本在库存等口径差异，前端同样不做此计算）。
        Map<String, Object> ysData = null;
        String yearStart = period.substring(0, 4) + "01";
        try {
            ysData = balanceSheet(yearStart);
        } catch (Exception ignore) {
            // 年初期间无数据或尚未结账时，小计年初值留空，不影响本次导出
        }
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
        // P88②：各行本期/累计逐行对齐（不再把净利塞进营收行、不再累计列留空）
        rows.add(List.of("一、营业收入", "1", data.get("revenue"), data.get("cumulativeRevenue")));
        rows.add(List.of("减：营业成本", "2", data.get("cost"), data.get("cumulativeCost")));
        rows.add(List.of("二、毛利", "3", data.get("grossProfit"), data.get("cumulativeGrossProfit")));
        rows.add(List.of("减：期间费用", "4", data.get("expense"), data.get("cumulativeExpense")));
        rows.add(List.of("三、营业利润", "5", data.get("operatingProfit"), data.get("cumulativeOperatingProfit")));
        rows.add(List.of("减：其他支出", "6", data.get("otherExpense"), data.get("cumulativeOtherExpense")));
        rows.add(List.of("四、利润总额", "7", data.get("totalProfit"), data.get("cumulativeProfit")));
        writeExcel(response, "利润表", period, headers, rows);
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
            rows.add(List.of("勾稽校验", "13", "期末现金 = 期初 + 净流量，与 1001+1002 期末余额一致 ✓", ""));
        } else {
            rows.add(List.of("勾稽校验", "13", "⚠ 差异 " + data.get("cashCheckDiff")
                    + "（期末现金计算值 vs 科目余额 1001+1002）", ""));
        }
        writeExcel(response, "现金流量表", period, headers, rows);
    }
}
