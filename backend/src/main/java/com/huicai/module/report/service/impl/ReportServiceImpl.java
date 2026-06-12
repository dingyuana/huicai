package com.huicai.module.report.service.impl;

import cn.hutool.core.io.IoUtil;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.huicai.module.report.mapper.ReportDataMapper;
import com.huicai.module.report.service.ReportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
        Map<String, Object> aggregate = reportDataMapper.balanceSheetAggregate(period);
        List<Map<String, Object>> balances = reportDataMapper.subjectBalance(period);

        // 按科目编码前缀分组
        List<Map<String, Object>> assets = new ArrayList<>();
        List<Map<String, Object>> liab = new ArrayList<>();
        List<Map<String, Object>> equity = new ArrayList<>();
        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalLiab = BigDecimal.ZERO;
        BigDecimal totalEquity = BigDecimal.ZERO;

        for (Map<String, Object> row : balances) {
            String code = (String) row.get("code");
            if (code == null) continue;
            BigDecimal balance = toBigDecimal(row.get("end_balance"));
            String direction = (String) row.get("direction");
            BigDecimal signedBalance = "debit".equals(direction) ? balance : balance.negate();
            if (code.startsWith("1")) {
                assets.add(row);
                totalAssets = totalAssets.add(signedBalance);
            } else if (code.startsWith("2") || code.startsWith("3")) {
                liab.add(row);
                totalLiab = totalLiab.add(signedBalance);
            } else if (code.startsWith("4")) {
                equity.add(row);
                totalEquity = totalEquity.add(signedBalance);
            }
        }
        result.put("period", period);
        result.put("assets", assets);
        result.put("liabilities", liab);
        result.put("equity", equity);
        result.put("totalAssets", totalAssets);
        result.put("totalLiabilities", totalLiab);
        result.put("totalEquity", totalEquity);
        result.put("totalLiabEquity", totalLiab.add(totalEquity));
        result.put("balanced", totalAssets.subtract(totalLiab).subtract(totalEquity).abs()
                .compareTo(new BigDecimal("0.01")) < 0);
        return result;
    }

    @Override
    public Map<String, Object> incomeStatement(String period) {
        Map<String, Object> data = reportDataMapper.incomeStatementData(period);
        String yearStart = period.substring(0, 4) + "01";
        Map<String, Object> cumulative = reportDataMapper.cumulativeData(yearStart, period);

        BigDecimal revenue = toBigDecimal(getOrNull(data, "revenue"))
                .subtract(toBigDecimal(getOrNull(data, "revenue_offset")));
        BigDecimal cost = toBigDecimal(getOrNull(data, "cost"));
        BigDecimal expense = toBigDecimal(getOrNull(data, "expense"));
        BigDecimal otherExpense = toBigDecimal(getOrNull(data, "other_expense"));

        BigDecimal grossProfit = revenue.subtract(cost);
        BigDecimal operatingProfit = grossProfit.subtract(expense);
        BigDecimal totalProfit = operatingProfit.subtract(otherExpense);

        BigDecimal cumRevenue = toBigDecimal(getOrNull(cumulative, "cumulative_revenue"));
        BigDecimal cumCost = toBigDecimal(getOrNull(cumulative, "cumulative_cost"));

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
        result.put("cumulativeProfit", cumRevenue.subtract(cumCost));
        return result;
    }

    private static Object getOrNull(Map<String, Object> map, String key) {
        return map == null ? null : map.get(key);
    }

    @Override
    public Map<String, Object> cashFlowStatement(String period) {
        List<Map<String, Object>> rows = reportDataMapper.cashFlowData(period);
        BigDecimal opIn = BigDecimal.ZERO;
        BigDecimal opOut = BigDecimal.ZERO;
        BigDecimal invIn = BigDecimal.ZERO;
        BigDecimal invOut = BigDecimal.ZERO;
        BigDecimal finIn = BigDecimal.ZERO;
        BigDecimal finOut = BigDecimal.ZERO;
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
        BigDecimal totalNet = opNet.add(invNet).add(finNet);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", period);
        result.put("operatingIn", opIn);
        result.put("operatingOut", opOut);
        result.put("operatingNet", opNet);
        result.put("investingIn", invIn);
        result.put("investingOut", invOut);
        result.put("investingNet", invNet);
        result.put("financingIn", finIn);
        result.put("financingOut", finOut);
        result.put("financingNet", finNet);
        result.put("totalNet", totalNet);
        return result;
    }

    @Override
    public List<Map<String, Object>> trend(String startPeriod, String endPeriod) {
        return reportDataMapper.trendData(startPeriod, endPeriod);
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        return new BigDecimal(o.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private void writeExcel(HttpServletResponse response, String fileName, String[] headers, List<List<Object>> rows) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + ".xlsx");
        ExcelWriter writer = ExcelUtil.getWriter(true);
        writer.addHeaderAlias("col", fileName);
        for (int i = 0; i < headers.length; i++) {
            writer.writeCellValue(i, 0, headers[i]);
        }
        for (int i = 0; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            for (int j = 0; j < row.size(); j++) {
                writer.writeCellValue(j, i + 1, row.get(j));
            }
        }
        writer.flush(response.getOutputStream());
        writer.close();
    }

    @Override
    public void exportSubjectBalance(String period, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> data = subjectBalanceTable(period);
        String[] headers = {"科目编码", "科目名称", "方向", "期初余额", "本期借方", "本期贷方", "期末余额"};
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : data) {
            rows.add(List.of(
                row.get("code"), row.get("name"), row.get("direction"),
                row.get("begin_balance"), row.get("debit_total"),
                row.get("credit_total"), row.get("end_balance")
            ));
        }
        writeExcel(response, "科目余额表_" + period, headers, rows);
    }

    @Override
    public void exportBalanceSheet(String period, HttpServletResponse response) throws IOException {
        Map<String, Object> data = balanceSheet(period);
        String[] headers = {"项目", "行次", "期末余额", "年初余额"};
        List<List<Object>> rows = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assets = (List<Map<String, Object>>) data.get("assets");
        for (Map<String, Object> a : assets) {
            rows.add(List.of(a.get("name"), "", a.get("end_balance"), a.get("begin_balance")));
        }
        rows.add(List.of("资产总计", "", data.get("totalAssets"), ""));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> liab = (List<Map<String, Object>>) data.get("liabilities");
        for (Map<String, Object> l : liab) {
            rows.add(List.of(l.get("name"), "", l.get("end_balance"), l.get("begin_balance")));
        }
        writeExcel(response, "资产负债表_" + period, headers, rows);
    }

    @Override
    public void exportIncomeStatement(String period, HttpServletResponse response) throws IOException {
        Map<String, Object> data = incomeStatement(period);
        String[] headers = {"项目", "行次", "本期金额", "本年累计"};
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("一、营业收入", "1", data.get("revenue"), data.get("cumulativeRevenue")));
        rows.add(List.of("减：营业成本", "2", data.get("cost"), data.get("cumulativeCost")));
        rows.add(List.of("二、营业利润", "3", data.get("operatingProfit"), data.get("cumulativeProfit")));
        rows.add(List.of("减：营业费用", "4", data.get("expense"), ""));
        rows.add(List.of("三、利润总额", "5", data.get("totalProfit"), ""));
        writeExcel(response, "利润表_" + period, headers, rows);
    }

    @Override
    public void exportCashFlow(String period, HttpServletResponse response) throws IOException {
        Map<String, Object> data = cashFlowStatement(period);
        String[] headers = {"项目", "行次", "本期金额"};
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("经营活动现金流入", "1", data.get("operatingIn")));
        rows.add(List.of("经营活动现金流出", "2", data.get("operatingOut")));
        rows.add(List.of("经营活动净额", "3", data.get("operatingNet")));
        rows.add(List.of("投资活动现金流入", "4", data.get("investingIn")));
        rows.add(List.of("投资活动现金流出", "5", data.get("investingOut")));
        rows.add(List.of("投资活动净额", "6", data.get("investingNet")));
        rows.add(List.of("筹资活动现金流入", "7", data.get("financingIn")));
        rows.add(List.of("筹资活动现金流出", "8", data.get("financingOut")));
        rows.add(List.of("筹资活动净额", "9", data.get("financingNet")));
        rows.add(List.of("现金净增加额", "10", data.get("totalNet")));
        writeExcel(response, "现金流量表_" + period, headers, rows);
    }
}
