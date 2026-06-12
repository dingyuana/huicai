package com.huicai.module.report.service.impl;

import com.huicai.module.report.mapper.ReportDataMapper;
import com.huicai.module.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

        BigDecimal revenue = toBigDecimal(data.get("revenue"))
                .subtract(toBigDecimal(data.get("revenue_offset")));
        BigDecimal cost = toBigDecimal(data.get("cost"));
        BigDecimal expense = toBigDecimal(data.get("expense"));
        BigDecimal otherExpense = toBigDecimal(data.get("other_expense"));

        BigDecimal grossProfit = revenue.subtract(cost);
        BigDecimal operatingProfit = grossProfit.subtract(expense);
        BigDecimal totalProfit = operatingProfit.subtract(otherExpense);

        BigDecimal cumRevenue = toBigDecimal(cumulative.get("cumulative_revenue"));
        BigDecimal cumCost = toBigDecimal(cumulative.get("cumulative_cost"));

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
}
