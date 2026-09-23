package com.huicai.base.report.service.impl;

import com.huicai.base.report.mapper.ReportDataMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock private ReportDataMapper reportDataMapper;
    @InjectMocks private ReportServiceImpl service;

    @Test
    void subjectBalanceTable_returns_list() {
        List<Map<String, Object>> mock = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("code", "1002"); row.put("name", "银行存款");
        row.put("debit", 1000.0); row.put("credit", 0.0);
        mock.add(row);
        when(reportDataMapper.subjectBalance("202606")).thenReturn(mock);

        List<Map<String, Object>> r = service.subjectBalanceTable("202606");
        assertEquals(1, r.size());
        assertEquals("1002", r.get(0).get("code"));
    }

    @Test
    void balanceSheet_returns_period_and_data() {
        when(reportDataMapper.subjectBalance("202606")).thenReturn(new ArrayList<>());

        Map<String, Object> r = service.balanceSheet("202606");
        assertNotNull(r);
        assertEquals("202606", r.get("period"));
        assertEquals(Boolean.TRUE, r.get("balanced"));
    }

    @Test
    void balanceSheet_groups_3xxx_and_4xxx_by_spec() {
        // P69: 3xxx 共同类借余入资产/贷余入负债；4xxx 权益
        List<Map<String, Object>> balances = new ArrayList<>();
        Map<String, Object> a1 = new HashMap<>(); a1.put("code", "1002"); a1.put("name", "银行存款"); a1.put("end_balance", 5000.0); a1.put("direction", "debit"); balances.add(a1);
        Map<String, Object> c1 = new HashMap<>(); c1.put("code", "3101"); c1.put("name", "共同贷余"); c1.put("end_balance", 3000.0); c1.put("direction", "credit"); balances.add(c1);
        Map<String, Object> e1 = new HashMap<>(); e1.put("code", "4001"); e1.put("name", "实收资本"); e1.put("end_balance", 1000.0); e1.put("direction", "credit"); balances.add(e1);
        Map<String, Object> e2 = new HashMap<>(); e2.put("code", "4002"); e2.put("name", "资本公积"); e2.put("end_balance", 500.0); e2.put("direction", "credit"); balances.add(e2);
        when(reportDataMapper.subjectBalance("202606")).thenReturn(balances);

        Map<String, Object> r = service.balanceSheet("202606");
        List<Map<String, Object>> assets = (List<Map<String, Object>>) r.get("assets");
        List<Map<String, Object>> liab = (List<Map<String, Object>>) r.get("liabilities");
        List<Map<String, Object>> equity = (List<Map<String, Object>>) r.get("equity");

        assertEquals(1, assets.size(), "1xxx 应归为资产");
        assertEquals("1002", assets.get(0).get("code"));
        assertEquals(1, liab.size(), "3xxx 贷余应归为负债");
        assertEquals("3101", liab.get(0).get("code"));
        assertEquals(2, equity.size(), "4xxx 应归为权益");
        assertEquals("4001", equity.get(0).get("code"));
        assertEquals("4002", equity.get(1).get("code"));
    }

    @Test
    void balanceSheet_totals_respect_subject_direction() {
        // 资产(1xxx, debit) + 权益(4xxx, credit): 期初建账典型场景
        List<Map<String, Object>> balances = new ArrayList<>();
        Map<String, Object> a1 = new HashMap<>(); a1.put("code", "1002"); a1.put("name", "银行存款"); a1.put("end_balance", 200000.0); a1.put("direction", "debit"); balances.add(a1);
        Map<String, Object> a2 = new HashMap<>(); a2.put("code", "1601"); a2.put("name", "固定资产"); a2.put("end_balance", 100000.0); a2.put("direction", "debit"); balances.add(a2);
        Map<String, Object> e1 = new HashMap<>(); e1.put("code", "4001"); e1.put("name", "实收资本"); e1.put("end_balance", 300000.0); e1.put("direction", "credit"); balances.add(e1);
        when(reportDataMapper.subjectBalance("202610")).thenReturn(balances);

        Map<String, Object> r = service.balanceSheet("202610");

        // 资产: 200000 + 100000 = 300000
        assertEquals(new BigDecimal("300000.00"), r.get("totalAssets"));
        // 负债: 无
        assertEquals(new BigDecimal("0.00"), r.get("totalLiabilities"));
        // 权益: credit 方向科目应为正数
        assertEquals(new BigDecimal("300000.00"), r.get("totalEquity"));
        assertEquals(new BigDecimal("300000.00"), r.get("totalLiabEquity"));
        // 资产 = 负债 + 权益, 应平衡
        assertEquals(Boolean.TRUE, r.get("balanced"));
    }

    @Test
    void balanceSheet_liability_credit_is_positive() {
        // 负债(2xxx, credit) 应为正数, 资产 = 负债 平衡场景
        List<Map<String, Object>> balances = new ArrayList<>();
        Map<String, Object> a1 = new HashMap<>(); a1.put("code", "1002"); a1.put("name", "银行存款"); a1.put("end_balance", 3000.0); a1.put("direction", "debit"); balances.add(a1);
        Map<String, Object> l1 = new HashMap<>(); l1.put("code", "2001"); l1.put("name", "短期借款"); l1.put("end_balance", 3000.0); l1.put("direction", "credit"); balances.add(l1);
        when(reportDataMapper.subjectBalance("202606")).thenReturn(balances);

        Map<String, Object> r = service.balanceSheet("202606");

        assertEquals(new BigDecimal("3000.00"), r.get("totalAssets"));
        assertEquals(new BigDecimal("3000.00"), r.get("totalLiabilities"));
        assertEquals(new BigDecimal("3000.00"), r.get("totalLiabEquity"));
        assertEquals(Boolean.TRUE, r.get("balanced"));
    }

    // ==================== P69 资产负债表恒等式 ====================

    private Map<String, Object> bal(String code, String name, String direction,
                                    double debitTotal, double creditTotal, double endBalance) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", code);
        m.put("name", name);
        m.put("direction", direction);
        m.put("debit_total", debitTotal);
        m.put("credit_total", creditTotal);
        m.put("end_balance", endBalance);
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> items(Map<String, Object> r, String key) {
        return (List<Map<String, Object>>) r.get(key);
    }

    @Test
    void p69_scenario1_uncarriedProfit_balancesWithCurrentYearProfit() {
        List<Map<String, Object>> b = new ArrayList<>();
        b.add(bal("1122", "应收账款", "debit", 11300, 0, 11300));
        b.add(bal("2221.01", "销项税额", "credit", 0, 1300, 1300));
        b.add(bal("6001", "主营业务收入", "credit", 0, 10000, 10000));
        when(reportDataMapper.subjectBalance("202606")).thenReturn(b);

        Map<String, Object> r = service.balanceSheet("202606");

        assertEquals(new BigDecimal("11300.00"), r.get("totalAssets"));
        assertEquals(new BigDecimal("1300.00"), r.get("totalLiabilities"));
        assertEquals(new BigDecimal("10000.00"), r.get("currentYearProfit"));
        assertEquals(new BigDecimal("11300.00"), r.get("totalLiabEquity"));
        assertEquals(Boolean.TRUE, r.get("balanced"));
        assertEquals(new BigDecimal("0.00"), r.get("diff"));
        assertEquals(0, items(r, "unbalancedItems").size());
        // P88①：本年利润(未结转 6001 → currentYearProfit=10000) 以合成行进入权益区；
        // 但绝不应出现 6001 自身名义的行（P69 不变量仍成立）
        assertEquals(1, items(r, "equity").size(), "未结转本年利润应产生 1 行合成权益行");
        assertEquals("本年利润(含未结转)", items(r, "equity").get(0).get("name"));
        assertEquals(new BigDecimal("10000.00"), items(r, "equity").get(0).get("end_balance"));
    }

    @Test
    void p69_scenario2_carriedProfit_notDoubleCounted() {
        List<Map<String, Object>> b = new ArrayList<>();
        b.add(bal("1002", "银行存款", "debit", 10000, 0, 10000));
        b.add(bal("4103", "本年利润", "credit", 0, 10000, 10000));
        b.add(bal("6001", "主营业务收入", "credit", 10000, 10000, 0));
        when(reportDataMapper.subjectBalance("202606")).thenReturn(b);

        Map<String, Object> r = service.balanceSheet("202606");

        assertEquals(new BigDecimal("10000.00"), r.get("currentYearProfit"),
                "已结转时本年利润行只取 4103，不与 6* 重复");
        assertEquals(new BigDecimal("10000.00"), r.get("totalEquity"));
        assertEquals(Boolean.TRUE, r.get("balanced"));
    }

    @Test
    void p69_scenario3_costClassifiedIntoInventory() {
        List<Map<String, Object>> b = new ArrayList<>();
        b.add(bal("1002", "银行存款", "debit", 5000, 0, 5000));
        b.add(bal("5001", "生产成本", "debit", 3000, 0, 3000));
        b.add(bal("4001", "实收资本", "credit", 0, 8000, 8000));
        when(reportDataMapper.subjectBalance("202606")).thenReturn(b);

        Map<String, Object> r = service.balanceSheet("202606");

        assertEquals(new BigDecimal("8000.00"), r.get("totalAssets"), "5001 借余计入存货资产");
        assertEquals(new BigDecimal("3000.00"), r.get("costInInventory"));
        assertEquals("5001", items(r, "assets").get(1).get("code"));
        assertEquals(Boolean.TRUE, r.get("balanced"));
    }

    @Test
    void p69_scenario4_commonClass3_fallsByDirection() {
        List<Map<String, Object>> b = new ArrayList<>();
        b.add(bal("3101", "共同借余", "debit", 2000, 0, 2000));
        b.add(bal("3102", "共同贷余", "credit", 0, 2000, 2000));
        when(reportDataMapper.subjectBalance("202606")).thenReturn(b);

        Map<String, Object> r = service.balanceSheet("202606");

        assertEquals(new BigDecimal("2000.00"), r.get("totalAssets"), "3* 借余入资产");
        assertEquals(new BigDecimal("2000.00"), r.get("totalLiabilities"), "3* 贷余入负债");
        assertEquals("3101", items(r, "assets").get(0).get("code"));
        assertEquals("3102", items(r, "liabilities").get(0).get("code"));
        assertEquals(Boolean.TRUE, r.get("balanced"));
    }

    @Test
    void p69_scenario5_unclassified_isDiagnosable() {
        // 借贷试算平衡(借5000=贷4000负债+贷1000异常科目)，但贷方异常科目落在 1-6 之外无法归类
        List<Map<String, Object>> b = new ArrayList<>();
        b.add(bal("1002", "银行存款", "debit", 5000, 0, 5000));
        b.add(bal("2001", "短期借款", "credit", 0, 4000, 4000));
        Map<String, Object> orphan = bal("9001", "无法归类科目", "credit", 0, 1000, 1000);
        b.add(orphan);
        when(reportDataMapper.subjectBalance("202606")).thenReturn(b);

        Map<String, Object> r = service.balanceSheet("202606");

        assertEquals(Boolean.FALSE, r.get("balanced"));
        assertEquals(new BigDecimal("1000.00"), r.get("diff"),
                "无法归类的贷方科目被排除在恒等式外，差额即其金额");
        List<Map<String, Object>> bad = items(r, "unbalancedItems");
        assertEquals(1, bad.size());
        assertEquals("9001", bad.get(0).get("code"));
    }

    @Test
    void p69_scenario5b_missingDirection_isDiagnosable() {
        List<Map<String, Object>> b = new ArrayList<>();
        b.add(bal("1002", "银行存款", "debit", 4000, 0, 4000));
        b.add(bal("2001", "短期借款", "credit", 0, 4000, 4000));
        Map<String, Object> noDir = new HashMap<>();
        noDir.put("code", "2241"); noDir.put("name", "缺方向科目"); noDir.put("end_balance", 1000.0);
        b.add(noDir);
        when(reportDataMapper.subjectBalance("202606")).thenReturn(b);

        Map<String, Object> r = service.balanceSheet("202606");

        assertEquals(Boolean.FALSE, r.get("balanced"));
        List<Map<String, Object>> bad = items(r, "unbalancedItems");
        assertEquals(1, bad.size());
        assertEquals("2241", bad.get(0).get("code"));
        assertEquals("missing-direction", bad.get(0).get("classifiedTo"));
    }

    @Test
    void p69_scenario7_singleAlgorithm_totalsInternallyConsistent() {
        List<Map<String, Object>> b = new ArrayList<>();
        b.add(bal("1002", "银行存款", "debit", 200000, 0, 200000));
        b.add(bal("1122", "应收账款", "debit", 85800, 0, 85800));
        b.add(bal("5001", "生产成本", "debit", 75929.20, 0, 75929.20));
        b.add(bal("2221.01", "销项税额", "credit", 0, 9870.80, 9870.80));
        b.add(bal("4001", "实收资本", "credit", 0, 300000, 300000));
        b.add(bal("6001", "主营业务收入", "credit", 0, 51858.40, 51858.40));
        when(reportDataMapper.subjectBalance("202401")).thenReturn(b);

        Map<String, Object> r = service.balanceSheet("202401");

        BigDecimal ta = (BigDecimal) r.get("totalAssets");
        BigDecimal tle = (BigDecimal) r.get("totalLiabEquity");
        assertEquals(0, ta.compareTo(tle), "明细归类合计与对外合计必须一致");
        assertEquals(Boolean.TRUE, r.get("balanced"));
    }

    @Test
    void p69_creditBalanceCostClass_showsNegativeInventoryButBalances() {
        List<Map<String, Object>> b = new ArrayList<>();
        b.add(bal("1002", "银行存款", "debit", 309870.80, 0, 309870.80));
        b.add(bal("5001", "生产成本误记贷方", "credit", 0, 75929.20, 75929.20));
        b.add(bal("4001", "实收资本", "credit", 0, 233941.60, 233941.60));
        when(reportDataMapper.subjectBalance("202401")).thenReturn(b);

        Map<String, Object> r = service.balanceSheet("202401");

        assertEquals(new BigDecimal("-75929.20"), r.get("costInInventory"),
                "误记会在存货上显性化为负值，不靠报表掩盖");
        assertEquals(new BigDecimal("233941.60"), r.get("totalAssets"));
        assertEquals(Boolean.TRUE, r.get("balanced"));
    }

    @Test
    void p69_emptyData_isBalancedZero() {
        when(reportDataMapper.subjectBalance("202606")).thenReturn(new ArrayList<>());

        Map<String, Object> r = service.balanceSheet("202606");

        assertEquals(new BigDecimal("0.00"), r.get("totalAssets"));
        assertEquals(new BigDecimal("0.00"), r.get("totalLiabEquity"));
        assertEquals(Boolean.TRUE, r.get("balanced"));
    }

    @Test
    void p69_expenseClass_reducesCurrentYearProfit() {
        List<Map<String, Object>> b = new ArrayList<>();
        b.add(bal("1002", "银行存款", "debit", 0, 27, 973));
        b.add(bal("6603", "财务费用", "debit", 27, 0, 27));
        b.add(bal("4001", "实收资本", "credit", 0, 0, 1000));
        when(reportDataMapper.subjectBalance("202606")).thenReturn(b);

        Map<String, Object> r = service.balanceSheet("202606");

        assertEquals(new BigDecimal("973.00"), r.get("totalAssets"));
        assertEquals(new BigDecimal("-27.00"), r.get("currentYearProfit"),
                "费用类损益科目必须冲减当期利润");
        assertEquals(new BigDecimal("973.00"), r.get("totalLiabEquity"));
        assertEquals(new BigDecimal("0.00"), r.get("diff"));
        assertEquals(Boolean.TRUE, r.get("balanced"));
    }

    @Test
    void incomeStatement_returns_period_map() {
        Map<String, Object> periodData = new HashMap<>();
        periodData.put("revenue", 10000.0);
        periodData.put("profit", 3000.0);
        when(reportDataMapper.incomeStatementData("202606")).thenReturn(periodData);
        when(reportDataMapper.cumulativeData("202601", "202606"))
                .thenReturn(new HashMap<>());

        Map<String, Object> r = service.incomeStatement("202606");
        assertNotNull(r);
        assertEquals("202606", r.get("period"));
    }

    @Test
    void cashFlowStatement_returns_period_map() {
        when(reportDataMapper.cashFlowData("202606")).thenReturn(new ArrayList<>());

        Map<String, Object> r = service.cashFlowStatement("202606");
        assertNotNull(r);
        assertEquals("202606", r.get("period"));
    }

    @Test
    void trend_returns_list_between_periods() {
        List<Map<String, Object>> trend = new ArrayList<>();
        Map<String, Object> p1 = new HashMap<>();
        p1.put("period", "202601"); p1.put("revenue", 10000.0); p1.put("profit", 2000.0);
        trend.add(p1);
        Map<String, Object> p2 = new HashMap<>();
        p2.put("period", "202602"); p2.put("revenue", 12000.0); p2.put("profit", 3000.0);
        trend.add(p2);
        when(reportDataMapper.trendData("202601", "202602")).thenReturn(trend);

        List<Map<String, Object>> r = service.trend("202601", "202602");
        assertEquals(2, r.size());
        assertEquals("202601", r.get(0).get("period"));
    }

    // ==================== P88 三表信任级缺陷回归（阻断复发） ====================

    @Test
    void p88_balanceSheet_lossYearProfitRow_appearsInEquity() {
        // 截图像：实收资本 200000 + 本年利润 -27（财务费用未结转）
        List<Map<String, Object>> b = new ArrayList<>();
        b.add(bal("1002", "银行存款", "debit", 0, 0, 124315.70));
        b.add(bal("1122", "应收账款", "debit", 0, 0, 88457.37));
        b.add(bal("2202", "预收账款", "credit", 0, 12800.07, 12800.07));
        b.add(bal("4001", "实收资本", "credit", 0, 200000.00, 200000.00));
        b.add(bal("6603", "财务费用", "debit", 27.00, 0, 27.00));
        when(reportDataMapper.subjectBalance("202607")).thenReturn(b);

        Map<String, Object> r = service.balanceSheet("202607");

        // 本年利润 = 6603 未结转 -27（profit4103=0, currentPeriodProfit=-27）
        List<Map<String, Object>> equity = items(r, "equity");
        boolean found = equity.stream().anyMatch(e ->
                "本年利润(含未结转)".equals(e.get("name"))
                        && new BigDecimal("-27.00").equals(e.get("end_balance")));
        assertTrue(found, "P88① 本年利润-27 必须以显式行出现在权益区（否则肉眼加总≠合计）");
        // 逐行加总 = 权益合计
        BigDecimal sumEquityRows = equity.stream()
                .map(e -> new BigDecimal(e.get("end_balance").toString())).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(r.get("totalEquity"), sumEquityRows, "权益区逐行加总必须等于权益合计");
        assertEquals(Boolean.TRUE, r.get("balanced"));
    }

    @Test
    void p88_incomeStatement_revenueIsGrossNotNet() {
        // 营业收入=credit 方向 6xx 发生额，绝不再减费用侧（旧版把净利塞进营收行）
        Map<String, Object> periodData = new HashMap<>();
        periodData.put("revenue", 10000.0);       // credit 6xx 毛收入
        periodData.put("revenue_offset", 0.0);
        periodData.put("cost", 3000.0);
        periodData.put("expense", 2000.0);         // 6601-6603 期间费用
        periodData.put("other_expense", 500.0);
        when(reportDataMapper.incomeStatementData("202607")).thenReturn(periodData);
        Map<String, Object> cum = new HashMap<>();
        cum.put("cumulative_revenue", 10000.0);
        cum.put("cumulative_cost", 3000.0);
        cum.put("cumulative_expense", 2000.0);
        cum.put("cumulative_other_expense", 500.0);
        when(reportDataMapper.cumulativeData("202601", "202607")).thenReturn(cum);

        Map<String, Object> r = service.incomeStatement("202607");

        assertEquals(new BigDecimal("10000.00"), r.get("revenue"), "营业收入必须是毛收入，不能扣费用");
        assertEquals(new BigDecimal("7000.00"), r.get("grossProfit"));
        assertEquals(new BigDecimal("5000.00"), r.get("operatingProfit"));
        assertEquals(new BigDecimal("4500.00"), r.get("totalProfit"));
        // 累计逐行对齐
        assertEquals(new BigDecimal("4500.00"), r.get("cumulativeProfit"), "累计利润总额必须扣全部费用");
        assertEquals(new BigDecimal("5000.00"), r.get("cumulativeOperatingProfit"));
    }

    @Test
    void p88_cashFlowStatement_closingLoopAndCheck() {
        when(reportDataMapper.cashFlowData("202607")).thenReturn(new ArrayList<>());
        Map<String, Object> cash = new HashMap<>();
        cash.put("begin_cash", 200000.00);
        cash.put("end_cash", 124315.70);
        when(reportDataMapper.cashSubjectBalance("202607")).thenReturn(cash);

        Map<String, Object> r = service.cashFlowStatement("202607");

        // 无流量数据 totalNet=0 → closingCash = 200000 + 0 = 200000
        assertEquals(new BigDecimal("200000.00"), r.get("openingCash"));
        assertEquals(new BigDecimal("200000.00"), r.get("closingCash"));
        // 勾稽：科目期末 124315.70 ≠ 计算期末 200000 → 差异 75684.30，应提示
        assertEquals(new BigDecimal("-75684.30"), r.get("cashCheckDiff"));
        assertEquals(Boolean.FALSE, r.get("cashCheckOk"), "期初+净流量 与 科目期末余额 不一致必须暴露差异");
    }

    @Test
    void p88_cashFlowStatement_checkOkWhenConsistent() {
        when(reportDataMapper.cashFlowData("202607")).thenReturn(new ArrayList<>());
        Map<String, Object> cash = new HashMap<>();
        cash.put("begin_cash", 200000.00);
        cash.put("end_cash", 200000.00);
        when(reportDataMapper.cashSubjectBalance("202607")).thenReturn(cash);

        Map<String, Object> r = service.cashFlowStatement("202607");
        assertEquals(Boolean.TRUE, r.get("cashCheckOk"), "期初+净流量(0)=期末，应校验通过");
        assertEquals(new BigDecimal("0.00"), r.get("cashCheckDiff"));
    }
}
