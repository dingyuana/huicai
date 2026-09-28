package com.huicai.base.report.service.impl;

import com.huicai.base.balance.service.SubjectBalanceService;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock private ReportDataMapper reportDataMapper;
    @Mock private SubjectBalanceService subjectBalanceService;
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
    void p92b_三分小计_按account_type正确归类() {
        // P92B-BD1：显式 account_type 优先；三分之和必须等于总计（P92B-BD2 勾稽）
        List<Map<String, Object>> b = new ArrayList<>();
        Map<String, Object> r;

        // 流动资产：货币资金 + 存货
        r = new HashMap<>(); r.put("code","1002"); r.put("name","银行存款"); r.put("direction","debit");
        r.put("end_balance",500000.0); r.put("account_type","CURRENT_ASSET"); b.add(r);
        r = new HashMap<>(); r.put("code","1403"); r.put("name","库存商品"); r.put("direction","debit");
        r.put("end_balance",420000.0); r.put("account_type","CURRENT_ASSET"); b.add(r);
        // 非流动资产：固定资产
        r = new HashMap<>(); r.put("code","1601"); r.put("name","固定资产"); r.put("direction","debit");
        r.put("end_balance",300000.0); r.put("account_type","NON_CURRENT_ASSET"); b.add(r);
        // account_type 缺失的在建工程：走科目段 fallback 归非流动（13 段）
        r = new HashMap<>(); r.put("code","1301"); r.put("name","在建工程"); r.put("direction","debit");
        r.put("end_balance",50000.0); r.put("account_type",null); b.add(r);

        // 流动负债
        r = new HashMap<>(); r.put("code","2202"); r.put("name","应付账款"); r.put("direction","credit");
        r.put("end_balance",150000.0); r.put("account_type","CURRENT_LIABILITY"); b.add(r);
        // 非流动负债
        r = new HashMap<>(); r.put("code","2401"); r.put("name","长期借款"); r.put("direction","credit");
        r.put("end_balance",100000.0); r.put("account_type","NON_CURRENT_LIABILITY"); b.add(r);
        // 专项储备：按会计准则本就列示于负债项下，编号 27 段走 fallback 归非流动负债
        r = new HashMap<>(); r.put("code","2701"); r.put("name","专项储备"); r.put("direction","credit");
        r.put("end_balance",20000.0); r.put("account_type",null); b.add(r);

        // 权益（不参与流动分类）
        r = new HashMap<>(); r.put("code","4001"); r.put("name","实收资本"); r.put("direction","credit");
        r.put("end_balance",1000000.0); r.put("account_type",null); b.add(r);

        when(reportDataMapper.subjectBalance("202606")).thenReturn(b);

        Map<String, Object> result = service.balanceSheet("202606");

        // 资产三分：流动 500000+420000=920000；非流动 300000+50000=350000；其他 0，合计 1270000
        assertEquals(new BigDecimal("920000.00"), result.get("currentAssets"));
        assertEquals(new BigDecimal("350000.00"), result.get("nonCurrentAssets"),
                "1601 固定资产 + 1301 在建工程（fallback）应同为非流动");
        assertEquals(new BigDecimal("0.00"), result.get("otherAssets"));
        assertEquals(new BigDecimal("1270000.00"), result.get("totalAssets"));

        // 负债三分：流动 150000；非流动 100000+20000=120000；其他 0，合计 270000
        assertEquals(new BigDecimal("150000.00"), result.get("currentLiabilities"));
        assertEquals(new BigDecimal("120000.00"), result.get("nonCurrentLiabilities"),
                "2701 专项储备按准则列于负债项下，27 段 fallback 归非流动");
        assertEquals(new BigDecimal("0.00"), result.get("otherLiabilities"));
        assertEquals(new BigDecimal("270000.00"), result.get("totalLiabilities"));

        // 权益不受流动分类影响，仍计入负债+权益合计
        assertEquals(new BigDecimal("1000000.00"), result.get("totalEquity"));
        assertEquals(new BigDecimal("1270000.00"), result.get("totalLiabEquity"));
        assertEquals(Boolean.TRUE, result.get("balanced"));
    }

    @Test
    void p92b_accountType缺失时走科目段兜底() {
        // P92B-BD3：迁移前数据或人工漏填 account_type 时不得静默丢弃，必须按科目段归类
        List<Map<String, Object>> b = new ArrayList<>();
        // 注意：不设置 account_type，全部走 fallback 科目段判定
        Map<String, Object> r;

        r = new HashMap<>(); r.put("code","1002"); r.put("name","银行存款"); r.put("direction","debit");
        r.put("end_balance",100000.0); b.add(r);
        r = new HashMap<>(); r.put("code","1501"); r.put("name","无形资产"); r.put("direction","debit");
        r.put("end_balance",30000.0); b.add(r);
        r = new HashMap<>(); r.put("code","5001"); r.put("name","生产成本"); r.put("direction","debit");
        r.put("end_balance",80000.0); b.add(r);
        r = new HashMap<>(); r.put("code","5301"); r.put("name","研发支出"); r.put("direction","debit");
        r.put("end_balance",20000.0); b.add(r);
        r = new HashMap<>(); r.put("code","2202"); r.put("name","应付账款"); r.put("direction","credit");
        r.put("end_balance",40000.0); b.add(r);
        r = new HashMap<>(); r.put("code","2501"); r.put("name","应付债券"); r.put("direction","credit");
        r.put("end_balance",60000.0); b.add(r);

        when(reportDataMapper.subjectBalance("202606")).thenReturn(b);

        Map<String, Object> result = service.balanceSheet("202606");

        // 1002(1x流动) + 5001(生产成本→流动存货) = 180000
        assertEquals(new BigDecimal("180000.00"), result.get("currentAssets"));
        // 1501(非流动) + 5301(研发支出→非流动) = 50000
        assertEquals(new BigDecimal("50000.00"), result.get("nonCurrentAssets"));
        assertEquals(new BigDecimal("0.00"), result.get("otherAssets"));
        assertEquals(new BigDecimal("230000.00"), result.get("totalAssets"));

        assertEquals(new BigDecimal("40000.00"), result.get("currentLiabilities"));
        assertEquals(new BigDecimal("60000.00"), result.get("nonCurrentLiabilities"));
        assertEquals(new BigDecimal("0.00"), result.get("otherLiabilities"));
    }

    @Test
    void p92b_1408段码不得重复计入流动与非流动() {
        // 回归：本套科目表 1408 是"委托加工物资"（属存货=流动资产），不是通用准则的
        // "持有待售资产"。fallback 中流动资产已含 startsWith("14")，若非流动再单列
        // startsWith("1408")，则 1408 会被两个独立 subtotal 同时匹配，重复计入小计。
        // 该重复不体现在勾稽上（其他由减法反推、总计按行加总），只能靠分段断言发现。
        List<Map<String, Object>> b = new ArrayList<>();
        Map<String, Object> r;

        r = new HashMap<>(); r.put("code","1002"); r.put("name","银行存款"); r.put("direction","debit");
        r.put("end_balance",100000.0); r.put("account_type","CURRENT_ASSET"); b.add(r);
        // 1408 委托加工物资：无 account_type，走 fallback，必须只落流动
        r = new HashMap<>(); r.put("code","1408"); r.put("name","委托加工物资"); r.put("direction","debit");
        r.put("end_balance",60000.0); b.add(r);
        r = new HashMap<>(); r.put("code","1501"); r.put("name","无形资产"); r.put("direction","debit");
        r.put("end_balance",30000.0); r.put("account_type","NON_CURRENT_ASSET"); b.add(r);

        when(reportDataMapper.subjectBalance("202606")).thenReturn(b);

        Map<String, Object> result = service.balanceSheet("202606");

        // 1002 + 1408 = 160000，全部归流动
        assertEquals(new BigDecimal("160000.00"), result.get("currentAssets"));
        // 非流动只有 1501。若 1408 被重复计入，这里会变成 90000。
        assertEquals(new BigDecimal("30000.00"), result.get("nonCurrentAssets"));
        assertEquals(new BigDecimal("0.00"), result.get("otherAssets"));
        // 三分之和必须等于资产总计（若 1408 重复计入，此处不等，显式断言兜住）
        BigDecimal ca = (BigDecimal) result.get("currentAssets");
        BigDecimal nca = (BigDecimal) result.get("nonCurrentAssets");
        BigDecimal oa = (BigDecimal) result.get("otherAssets");
        BigDecimal ta = (BigDecimal) result.get("totalAssets");
        assertEquals(ta, ca.add(nca).add(oa),
                "三分之和须等于资产总计；不等说明存在科目被重复计入小计");
        assertEquals(new BigDecimal("190000.00"), ta);
    }

    // ==================== P93 未分配利润（4103 + 4104） ====================

    @Test
    void p93_4104计入未分配利润且不单独成行() {
        // 回归：4104 利润分配必须并入"未分配利润"合成行，且不得在权益区单独出现一行。
        // 若误把 4104 加进 equity loop，会出现 4104 独立行 + 未分配利润行，权益合计虚增 5000。
        List<Map<String, Object>> b = new ArrayList<>();
        b.add(bal("1002", "银行存款", "debit", 0, 0, 25000));
        b.add(bal("4001", "实收资本", "credit", 0, 10000, 10000));
        b.add(bal("4103", "本年利润", "credit", 0, 5000, 5000));
        b.add(bal("4104", "利润分配", "credit", 0, 10000, 10000));
        when(reportDataMapper.subjectBalance("202606")).thenReturn(b);

        Map<String, Object> r = service.balanceSheet("202606");

        assertEquals(new BigDecimal("25000.00"), r.get("totalEquity"), "4001(10000)+4103(5000)+4104(10000)=25000");
        assertEquals(new BigDecimal("15000.00"), r.get("currentYearProfit"));
        assertEquals(Boolean.TRUE, r.get("balanced"));
        // 权益区只有 4001 一行 + 未分配利润合成行；不得出现 4104 独立行
        List<Map<String, Object>> eq = items(r, "equity");
        assertEquals(0, eq.stream()
                .filter(e -> "4104".equals(e.get("code")))
                .count(), "4104 不得作为独立行进入权益区，否则会重复计入");
        boolean hasUndistributed = eq.stream().anyMatch(e ->
                "未分配利润".equals(e.get("name"))
                        && new BigDecimal("15000.00").equals(e.get("end_balance")));
        assertTrue(hasUndistributed, "未分配利润=4103(5000)+4104(10000)=15000");
    }

    @Test
    void p93_4104为负累计亏损时正确抵消() {
        // 累计亏损：4103 本年盈利 20000，4104 上年累计亏损 -8000，未分配利润净 12000；
        // 实收资本 4001 另有 8000，权益合计 = 8000 + 12000 = 20000，故资产端须取 20000 才平。
        List<Map<String, Object>> b = new ArrayList<>();
        b.add(bal("1002", "银行存款", "debit", 0, 0, 20000));
        b.add(bal("4001", "实收资本", "credit", 0, 8000, 8000));
        b.add(bal("4103", "本年利润", "credit", 0, 20000, 20000));
        b.add(bal("4104", "利润分配", "debit", 8000, 0, 8000));
        when(reportDataMapper.subjectBalance("202606")).thenReturn(b);

        Map<String, Object> r = service.balanceSheet("202606");

        assertEquals(new BigDecimal("20000.00"), r.get("totalEquity"), "8000(4001) + 20000(4103) - 8000(4104) = 20000");
        assertEquals(new BigDecimal("12000.00"), r.get("currentYearProfit"));
        assertEquals(Boolean.TRUE, r.get("balanced"));
        List<Map<String, Object>> eq = items(r, "equity");
        assertEquals(0, eq.stream().filter(e -> "4104".equals(e.get("code"))).count());
        boolean hasUndistributed = eq.stream().anyMatch(e ->
                "未分配利润".equals(e.get("name"))
                        && new BigDecimal("12000.00").equals(e.get("end_balance")));
        assertTrue(hasUndistributed);
    }

    @Test
    void p93_4104未建科目时不影响既有口径() {
        // 当前 DB 尚无 4104 科目。此场景验证：只有 4103 时行为与 P93 前一致（0 值不进合成行）。
        List<Map<String, Object>> b = new ArrayList<>();
        b.add(bal("1002", "银行存款", "debit", 0, 0, 10000));
        b.add(bal("4001", "实收资本", "credit", 0, 10000, 10000));
        b.add(bal("4103", "本年利润", "credit", 10000, 10000, 0));
        when(reportDataMapper.subjectBalance("202606")).thenReturn(b);

        Map<String, Object> r = service.balanceSheet("202606");

        assertEquals(new BigDecimal("10000.00"), r.get("totalEquity"));
        assertEquals(new BigDecimal("0.00"), r.get("currentYearProfit"));
        assertEquals(Boolean.TRUE, r.get("balanced"));
        assertEquals(1, items(r, "equity").size(), "0 值未分配利润不占行，仅有 4001");
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

    // ==================== P94 REQ-090 年初口径统一（begin 口径） ====================

    /**
     * 年初列必须取 1 月期初（begin_balance），旧实现误取 1 月期末（end_balance）。
     * 造数让 1 月有发生额，两种口径结果必然不同，测试才有证伪力。
     */
    @Test
    void p94_beginBalanceSheet_yearStartSubtotalEqualsDetailSum() {
        List<Map<String, Object>> jan = new ArrayList<>();
        jan.add(balB("1002", "银行存款", "debit", 80000, 20000, 0, 120000));
        List<Map<String, Object>> jun = new ArrayList<>();
        jun.add(balB("1002", "银行存款", "debit", 120000, 30000, 0, 150000));
        when(reportDataMapper.subjectBalance("202601")).thenReturn(jan);
        when(reportDataMapper.subjectBalance("202606")).thenReturn(jun);

        Map<String, Object> r = service.balanceSheet("202606");

        Map<String, Object> ys = yearStart(r);
        assertEquals("202601", ys.get("period"));
        assertEquals(new BigDecimal("80000.00"), ys.get("currentAssets"),
                "年初流动资产 = 1月期初 80000（明细行 1002 年初值同源）");
        assertEquals(new BigDecimal("80000.00"), ys.get("totalAssets"), "年初资产总计同口径");
        assertNotEquals(new BigDecimal("120000.00"), ys.get("currentAssets"),
                "负向：年初列不得等于 1 月期末 120000（旧口径必须被证伪）");
        assertEquals(new BigDecimal("150000.00"), r.get("currentAssets"), "期末列不受年初口径改动影响");
    }

    @Test
    void p94_yearStartIdentity_balanced_年初恒等式成立() {
        List<Map<String, Object>> jan = new ArrayList<>();
        jan.add(balB("1002", "银行存款", "debit", 80000, 0, 0, 80000));
        jan.add(balB("2001", "短期借款", "credit", 30000, 0, 0, 30000));
        jan.add(balB("4001", "实收资本", "credit", 50000, 0, 0, 50000));
        when(reportDataMapper.subjectBalance("202601")).thenReturn(jan);
        when(reportDataMapper.subjectBalance("202606")).thenReturn(new ArrayList<>());

        Map<String, Object> r = service.balanceSheet("202606");

        Map<String, Object> ys = yearStart(r);
        assertEquals(new BigDecimal("0.00"), r.get("yearStartCheckDiff"),
                "年初 资产80000 = 负债30000 + 权益50000，恒等式差异为 0");
        assertEquals(Boolean.TRUE, r.get("yearStartCheckOk"));
        assertEquals(new BigDecimal("80000.00"), ys.get("totalLiabEquity"));
    }

    @Test
    void p94_yearStartIdentity_unbalanced_仅提示不阻断() {
        List<Map<String, Object>> jan = new ArrayList<>();
        jan.add(balB("1002", "银行存款", "debit", 80000, 0, 0, 80000));
        when(reportDataMapper.subjectBalance("202601")).thenReturn(jan);
        when(reportDataMapper.subjectBalance("202606")).thenReturn(new ArrayList<>());

        Map<String, Object> r = service.balanceSheet("202606");

        assertEquals(new BigDecimal("80000.00"), r.get("yearStartCheckDiff"),
                "年初资产 80000 无对应负债权益，差异应如实暴露");
        assertEquals(Boolean.FALSE, r.get("yearStartCheckOk"));
        assertNotNull(r.get("yearStart"), "负向：不平只提示，年初区块仍须返回，不得抛错阻断报表");
    }

    @Test
    void p94_yearStart_未分配利润不含当期损益发生额() {
        // 1 月有 3 万收入：期末未分配利润含它（4103+4104+当期6xx），年初未分配利润不得含——
        // 损益类科目在期末结转后年初余额为 0，上年留存由 4104 期初承载。
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(balB("1002", "银行存款", "debit", 80000, 0, 0, 80000));
        rows.add(balB("4001", "实收资本", "credit", 50000, 0, 0, 50000));
        rows.add(balB("6001", "主营业务收入", "credit", 0, 0, 30000, 30000));
        when(reportDataMapper.subjectBalance("202601")).thenReturn(rows);
        when(reportDataMapper.subjectBalance("202606")).thenReturn(rows);

        Map<String, Object> r = service.balanceSheet("202606");
        Map<String, Object> ys = yearStart(r);

        assertEquals(new BigDecimal("30000.00"), r.get("currentYearProfit"), "期末含 1 月收入 30000");
        assertEquals(1, ((List<?>) r.get("equity")).stream()
                        .filter(e -> "未分配利润".equals(((Map<?, ?>) e).get("name"))).count(),
                "期末权益区应有未分配利润行");
        assertEquals(new BigDecimal("0.00"), ys.get("currentYearProfit"), "负向：年初未分配利润不得含当期损益");
        assertEquals(0, ((List<?>) ys.get("equity")).stream()
                        .filter(e -> "未分配利润".equals(((Map<?, ?>) e).get("name"))).count(),
                "负向：年初权益区不得出现未分配利润行（4001 实收资本行不受影响）");
    }

    /**
     * 悬空保护需要知道每行属于哪个小计，但分组口径只在 subtotal() 里；
     * 让前端自己按科目段猜会复制一份口径（P89-B 已记录前端重算必然算错）。
     */
    @Test
    void p94_subtotalGroup_行标记所属小计() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(balB("1002", "银行存款", "debit", 80000, 0, 0, 80000));
        rows.add(balB("1601", "固定资产", "debit", 50000, 0, 0, 50000));
        rows.add(balB("2001", "短期借款", "credit", 30000, 0, 0, 30000));
        when(reportDataMapper.subjectBalance("202601")).thenReturn(rows);
        when(reportDataMapper.subjectBalance("202606")).thenReturn(rows);

        Map<String, Object> r = service.balanceSheet("202606");
        Map<String, Object> ys = yearStart(r);

        assertEquals("CURRENT_ASSET", groupOf(ys, "assets", "1002"));
        assertEquals("NON_CURRENT_ASSET", groupOf(ys, "assets", "1601"));
        assertEquals("CURRENT_LIABILITY", groupOf(ys, "liabilities", "2001"));
        assertEquals("CURRENT_ASSET", groupOf(r, "assets", "1002"), "期末区块同样带分组标记");
    }

    /**
     * P94 REQ-092：页面级 Alert 由 cashCheckOk 驱动，容差边界必须锁死。
     * 判定是严格小于 0.01——恰好 0.01 视为不平（对账惯例），改成 <= 会让 Alert 在边界漏报。
     */
    @Test
    void p94_cashCheck_容差边界驱动页面Alert() {
        when(reportDataMapper.cashFlowData(anyString(), anyString())).thenReturn(new ArrayList<>());
        Map<String, Object> cash = new HashMap<>();
        cash.put("begin_cash", new BigDecimal("1000"));
        cash.put("end_cash", new BigDecimal("1200"));
        when(reportDataMapper.cashSubjectBalance(anyString())).thenReturn(cash);

        Map<String, Object> unbalanced = service.cashFlowStatement("202606");
        assertEquals(new BigDecimal("200.00"), unbalanced.get("cashCheckDiff"));
        assertEquals(Boolean.FALSE, unbalanced.get("cashCheckOk"), "差异 200 必须判不平以触发 Alert");

        cash.put("end_cash", new BigDecimal("1000.01"));
        Map<String, Object> boundary = service.cashFlowStatement("202606");
        assertEquals(new BigDecimal("0.01"), boundary.get("cashCheckDiff"));
        assertEquals(Boolean.FALSE, boundary.get("cashCheckOk"), "恰好 0.01 属不平（严格小于容差）");

        cash.put("end_cash", new BigDecimal("1000"));
        Map<String, Object> balanced = service.cashFlowStatement("202606");
        assertEquals(Boolean.TRUE, balanced.get("cashCheckOk"), "负向：无差异时不得弹 Alert");
    }

    /**
     * P97 阶段 A / REQ-099：利润表链式勾稽。
     * 旧口径把贷方 6xx 全算进营收、漏 6403 税金与 6801 所得税 → 利润总额虚高且无净利润。
     * 本用例钉死企业会计准则的完整链条，并断言营业外收入不进营收。
     */
    @Test
    void p97_incomeStatement_利润表链式勾稽() {
        Map<String, Object> d = new HashMap<>();
        d.put("revenue", new BigDecimal("12000"));            // 6001+6051
        d.put("cost", new BigDecimal("7000"));                // 6401+6402
        d.put("tax_and_surcharge", new BigDecimal("500"));     // 6403
        d.put("selling_expense", new BigDecimal("1000"));     // 6601
        d.put("admin_expense", new BigDecimal("2000"));       // 6602
        d.put("financial_expense", new BigDecimal("300"));     // 6603
        d.put("rd_expense", new BigDecimal("4000"));          // 6604
        d.put("other_income", new BigDecimal("300"));          // 6117
        d.put("investment_income", new BigDecimal("200"));     // 6111
        d.put("fair_value_income", BigDecimal.ZERO);          // 6101
        d.put("asset_disposal_income", BigDecimal.ZERO);      // 6115
        d.put("asset_impairment_loss", new BigDecimal("100")); // 6701
        d.put("non_operating_income", new BigDecimal("5000"));// 6301
        d.put("non_operating_expense", new BigDecimal("700"));// 6711
        d.put("income_tax", new BigDecimal("2500"));          // 6801
        when(reportDataMapper.incomeStatementData("202606")).thenReturn(d);
        when(reportDataMapper.cumulativeData("202601", "202606")).thenReturn(new HashMap<>());

        Map<String, Object> r = service.incomeStatement("202606");
        assertEquals(new BigDecimal("12000.00"), r.get("revenue"),
                "负向：营业收入只含 6001+6051，营业外收入 5000 不得计入（旧口径为 17000）");
        assertEquals(new BigDecimal("7300.00"), r.get("periodExpense"), "期间费用 = 销售+管理+财务+研发");
        // 12000 - 7000 - 500 - 7300 + 300 + 200 + 0 + 0 - 100 = -2400
        assertEquals(new BigDecimal("-2400.00"), r.get("operatingProfit"));
        // -2400 + 5000 - 700 = 1900
        assertEquals(new BigDecimal("1900.00"), r.get("totalProfit"));
        // 1900 - 2500 = -600
        assertEquals(new BigDecimal("-600.00"), r.get("netProfit"), "净利润 = 利润总额 − 所得税");
    }

    @Test
    void p97_incomeStatement_累计列与本期同口径() {
        Map<String, Object> cur = new HashMap<>();
        cur.put("revenue", new BigDecimal("1000"));
        Map<String, Object> cum = new HashMap<>();
        cum.put("cumulative_revenue", new BigDecimal("9000"));
        cum.put("cumulative_income_tax", new BigDecimal("500"));
        when(reportDataMapper.incomeStatementData("202606")).thenReturn(cur);
        when(reportDataMapper.cumulativeData("202601", "202606")).thenReturn(cum);

        Map<String, Object> r = service.incomeStatement("202606");

        assertEquals(new BigDecimal("1000.00"), r.get("revenue"));
        assertEquals(new BigDecimal("9000.00"), r.get("cumulativeRevenue"));
        assertEquals(new BigDecimal("8500.00"), r.get("cumulativeNetProfit"),
                "累计净利润 = 累计利润总额 − 累计所得税");
    }

    /**
     * P97 阶段 D / REQ-098：报表重分类（列报层）。
     *
     * <p>场景：预付账款(1123) 出现贷方余额 5000（收到退款，实质是预收）。
     * 配银行存款 5000 使恒等式在「开/关」两种口径下都成立——
     * 否则测试数据本身不平，balanced 断言会变成在测数据而不是测重分类。
     */
    private List<Map<String, Object>> reclassifyRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(balB("1123", "预付账款", "debit", -5000, 0, 5000, -5000));
        rows.add(balB("1002", "银行存款", "debit", 5000, 0, 5000, 5000));
        return rows;
    }

    @Test
    void p98_reclassify_默认关闭时负余额仍留资产列() {
        when(reportDataMapper.subjectBalance("202606")).thenReturn(reclassifyRows());
        when(reportDataMapper.subjectBalance("202601")).thenReturn(new ArrayList<>());

        Map<String, Object> r = service.balanceSheet("202606");

        assertEquals(Boolean.FALSE, r.get("reclassifyEnabled"), "默认必须关闭重分类");
        assertEquals(new BigDecimal("0.00"), r.get("totalAssets"), "关闭时 1123 与 1002 在资产列相抵");
        assertTrue(items(r, "liabilities").isEmpty(), "关闭时不得出现负债行");
        assertTrue(items(r, "assets").stream().anyMatch(x -> "1123".equals(x.get("code"))),
                "关闭时负余额行仍在资产列（现状口径）");
    }

    @Test
    void p98_reclassify_开启后预付贷方重分类为负债() {
        when(reportDataMapper.subjectBalance("202606")).thenReturn(reclassifyRows());
        when(reportDataMapper.subjectBalance("202601")).thenReturn(new ArrayList<>());

        Map<String, Object> r = service.balanceSheetWithReclassification("202606");

        assertEquals(Boolean.TRUE, r.get("reclassifyEnabled"), "开启后须在结果中标记，供报表抬头显示状态");
        assertEquals(new BigDecimal("5000.00"), r.get("totalAssets"), "重分类后资产侧只剩银行存款");
        assertEquals(new BigDecimal("5000.00"), r.get("totalLiabilities"), "重分类后负债侧为 5000");
        assertTrue(items(r, "assets").stream().noneMatch(x -> "1123".equals(x.get("code"))),
                "负余额行不得留在资产列");
        List<Map<String, Object>> liab = items(r, "liabilities");
        assertEquals(1, liab.size());
        assertEquals("1123", liab.get(0).get("code"));
        assertEquals(Boolean.TRUE, liab.get(0).get("reclassified"), "须标记该行由重分类而来");
        assertEquals(new BigDecimal("5000.00"), liab.get(0).get("end_balance"), "负债列示金额取绝对值");
        assertEquals(Boolean.TRUE, r.get("balanced"), "重分类不破坏资产负债恒等式");
        assertEquals(new BigDecimal("5000.00"), r.get("totalLiabEquity"));
    }

    @Test
    void p98_reclassify_开启与关闭的权益损益完全一致() {
        when(reportDataMapper.subjectBalance("202606")).thenReturn(reclassifyRows());
        when(reportDataMapper.subjectBalance("202601")).thenReturn(new ArrayList<>());

        Map<String, Object> off = service.balanceSheet("202606");
        Map<String, Object> on = service.balanceSheetWithReclassification("202606");

        assertEquals(off.get("currentYearProfit"), on.get("currentYearProfit"), "重分类不得影响损益");
        assertEquals(off.get("totalEquity"), on.get("totalEquity"), "重分类不得影响权益合计");
        // 只读铁律：本方法全程仅调用 subjectBalance 读接口，凭证/余额零写入
    }

    // ==================== P97 阶段 E / REQ-100 + REQ-102 诊断黄条 ====================

    private List<Map<String, Object>> diagIds(String period) {
        return service.diagnostics(period).stream()
                .map(d -> Map.<String, Object>of("ruleId", d.get("ruleId"), "title", d.get("title")))
                .collect(Collectors.toList());
    }

    private boolean hasRule(String period, String ruleId) {
        return diagIds(period).stream().anyMatch(d -> ruleId.equals(d.get("ruleId")));
    }

    @Test
    void p100_diagnostics_零收入但有费用时提示() {
        Map<String, Object> d = new HashMap<>();
        d.put("revenue", BigDecimal.ZERO);
        d.put("selling_expense", new BigDecimal("3000"));
        d.put("cost", BigDecimal.ZERO);
        d.put("non_operating_income", BigDecimal.ZERO);
        d.put("non_operating_expense", BigDecimal.ZERO);
        d.put("income_tax", BigDecimal.ZERO);
        when(reportDataMapper.incomeStatementData("202606")).thenReturn(d);
        when(reportDataMapper.cumulativeData("202601", "202606")).thenReturn(new HashMap<>());
        Map<String, Object> cash = new HashMap<>();
        cash.put("begin_cash", new BigDecimal("10000"));
        cash.put("end_cash", new BigDecimal("10000"));
        when(reportDataMapper.cashSubjectBalance("202606")).thenReturn(cash);
        when(subjectBalanceService.checkOpeningContinuity("202606")).thenReturn(new HashMap<>());

        assertTrue(hasRule("202606", "R_REVENUE_ZERO"), "零收入且有费用必须提示");
    }

    @Test
    void p100_diagnostics_收入正常时不误报() {
        Map<String, Object> d = new HashMap<>();
        d.put("revenue", new BigDecimal("50000"));
        d.put("selling_expense", new BigDecimal("3000"));
        d.put("cost", new BigDecimal("1000"));
        d.put("non_operating_income", BigDecimal.ZERO);
        d.put("non_operating_expense", BigDecimal.ZERO);
        d.put("income_tax", BigDecimal.ZERO);
        when(reportDataMapper.incomeStatementData("202606")).thenReturn(d);
        when(reportDataMapper.cumulativeData("202601", "202606")).thenReturn(new HashMap<>());
        Map<String, Object> cash = new HashMap<>();
        cash.put("begin_cash", new BigDecimal("10000"));
        cash.put("end_cash", new BigDecimal("12000"));
        when(reportDataMapper.cashSubjectBalance("202606")).thenReturn(cash);
        when(subjectBalanceService.checkOpeningContinuity("202606")).thenReturn(new HashMap<>());

        assertTrue(diagIds("202606").isEmpty(), "负向：收入正常、现金未骤降、期初连续时不得产出任何诊断");
    }

    @Test
    void p100_diagnostics_期末现金骤降超半数提示() {
        Map<String, Object> d = new HashMap<>();
        d.put("revenue", new BigDecimal("50000"));
        d.put("cost", BigDecimal.ZERO);
        d.put("non_operating_income", BigDecimal.ZERO);
        d.put("non_operating_expense", BigDecimal.ZERO);
        d.put("income_tax", BigDecimal.ZERO);
        when(reportDataMapper.incomeStatementData("202606")).thenReturn(d);
        when(reportDataMapper.cumulativeData("202601", "202606")).thenReturn(new HashMap<>());
        Map<String, Object> cash = new HashMap<>();
        cash.put("begin_cash", new BigDecimal("100000"));
        cash.put("end_cash", new BigDecimal("40000"));
        when(reportDataMapper.cashSubjectBalance("202606")).thenReturn(cash);
        when(subjectBalanceService.checkOpeningContinuity("202606")).thenReturn(new HashMap<>());

        assertTrue(hasRule("202606", "R_CASH_DROP"), "期末现金不足期初一半必须提示");
    }

    @Test
    void p102_diagnostics_期初不连续转为页条诊断() {
        Map<String, Object> d = new HashMap<>();
        d.put("revenue", new BigDecimal("50000"));
        d.put("cost", BigDecimal.ZERO);
        d.put("non_operating_income", BigDecimal.ZERO);
        d.put("non_operating_expense", BigDecimal.ZERO);
        d.put("income_tax", BigDecimal.ZERO);
        when(reportDataMapper.incomeStatementData("202606")).thenReturn(d);
        when(reportDataMapper.cumulativeData("202601", "202606")).thenReturn(new HashMap<>());
        Map<String, Object> cash = new HashMap<>();
        cash.put("begin_cash", new BigDecimal("10000"));
        cash.put("end_cash", new BigDecimal("10000"));
        when(reportDataMapper.cashSubjectBalance("202606")).thenReturn(cash);
        Map<String, Object> oc = new HashMap<>();
        oc.put("checked", true);
        oc.put("passed", false);
        oc.put("mismatchCount", 2);
        oc.put("maxAbsDiff", new BigDecimal("100000.00"));
        oc.put("mismatches", List.of(Map.of("subjectCode", "4001", "diff", new BigDecimal("-100000.00"))));
        when(subjectBalanceService.checkOpeningContinuity("202606")).thenReturn(oc);

        assertTrue(hasRule("202606", "R_OPENING_DISCONTINUITY"), "期初不连续须转为页条诊断（REQ-102）");
    }

    @Test
    void p100_diagnostics_纯只读不改数() {
        Map<String, Object> d = new HashMap<>();
        d.put("revenue", BigDecimal.ZERO);
        d.put("cost", BigDecimal.ZERO);
        d.put("non_operating_income", BigDecimal.ZERO);
        d.put("non_operating_expense", BigDecimal.ZERO);
        d.put("income_tax", BigDecimal.ZERO);
        when(reportDataMapper.incomeStatementData("202606")).thenReturn(d);
        when(reportDataMapper.cumulativeData("202601", "202606")).thenReturn(new HashMap<>());
        when(reportDataMapper.cashSubjectBalance("202606")).thenReturn(new HashMap<>());
        when(subjectBalanceService.checkOpeningContinuity("202606")).thenReturn(new HashMap<>());

        service.diagnostics("202606");

        // 只读铁律（ReportDataMapper 是纯 @Mapper 接口无写方法，故用交互白名单断言）：
        // 除三个只读查询外不得有任何其他调用——一旦诊断里混进写操作即失败
        verify(reportDataMapper, times(1)).incomeStatementData("202606");
        verify(reportDataMapper, times(1)).cumulativeData("202601", "202606");
        verify(reportDataMapper, times(1)).cashSubjectBalance("202606");
        verify(subjectBalanceService, times(1)).checkOpeningContinuity("202606");
        verifyNoMoreInteractions(reportDataMapper, subjectBalanceService);
    }

    private static String groupOf(Map<String, Object> block, String listKey, String code) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) block.get(listKey);
        return list.stream()
                .filter(r -> code.equals(r.get("code")))
                .map(r -> String.valueOf(r.get("subtotalGroup")))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> yearStart(Map<String, Object> balanceSheetResult) {
        Object ys = balanceSheetResult.get("yearStart");
        assertNotNull(ys, "balanceSheet 响应必须含 yearStart 区块（后端 begin 口径聚合）");
        return (Map<String, Object>) ys;
    }

    /** 带期初余额的行；bal() 不含 begin_balance，故年初口径用例需本 helper。 */
    private Map<String, Object> balB(String code, String name, String direction,
                                     double beginBalance, double debitTotal,
                                     double creditTotal, double endBalance) {
        Map<String, Object> m = bal(code, name, direction, debitTotal, creditTotal, endBalance);
        m.put("begin_balance", beginBalance);
        return m;
    }

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
        assertEquals("未分配利润", items(r, "equity").get(0).get("name"));
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
        when(reportDataMapper.cashFlowData("202606", "202606")).thenReturn(new ArrayList<>());
        when(reportDataMapper.cashFlowData("202601", "202606")).thenReturn(new ArrayList<>());

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
                "未分配利润".equals(e.get("name"))
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
        periodData.put("revenue", 10000.0);            // 6001+6051 营业收入（P97 起按段位枚举，不再是 6% 一锅端）
        periodData.put("cost", 3000.0);                // 6401+6402
        periodData.put("selling_expense", 2000.0);     // 6601 期间费用代表项
        periodData.put("non_operating_expense", 500.0);// 6711
        when(reportDataMapper.incomeStatementData("202607")).thenReturn(periodData);
        Map<String, Object> cum = new HashMap<>();
        cum.put("cumulative_revenue", 10000.0);
        cum.put("cumulative_cost", 3000.0);
        cum.put("cumulative_selling_expense", 2000.0);
        cum.put("cumulative_non_operating_expense", 500.0);
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
        when(reportDataMapper.cashFlowData("202607", "202607")).thenReturn(new ArrayList<>());
        when(reportDataMapper.cashFlowData("202601", "202607")).thenReturn(new ArrayList<>());
        Map<String, Object> cash = new HashMap<>();
        cash.put("begin_cash", 200000.00);
        cash.put("end_cash", 124315.70);
        when(reportDataMapper.cashSubjectBalance("202607")).thenReturn(cash);
        // P92-A：年初期间(202601)的期初现金，用于本年累计列
        Map<String, Object> cashYearStart = new HashMap<>();
        cashYearStart.put("begin_cash", 150000.00);
        cashYearStart.put("end_cash", 124315.70);
        when(reportDataMapper.cashSubjectBalance("202601")).thenReturn(cashYearStart);

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
        when(reportDataMapper.cashFlowData("202607", "202607")).thenReturn(new ArrayList<>());
        when(reportDataMapper.cashFlowData("202601", "202607")).thenReturn(new ArrayList<>());
        Map<String, Object> cash = new HashMap<>();
        cash.put("begin_cash", 200000.00);
        cash.put("end_cash", 200000.00);
        when(reportDataMapper.cashSubjectBalance("202607")).thenReturn(cash);
        // P92-A：年初期间期初现金（与本期期末勾稽，应一致）
        Map<String, Object> cashYearStart = new HashMap<>();
        cashYearStart.put("begin_cash", 150000.00);
        cashYearStart.put("end_cash", 200000.00);
        when(reportDataMapper.cashSubjectBalance("202601")).thenReturn(cashYearStart);

        Map<String, Object> r = service.cashFlowStatement("202607");
        assertEquals(Boolean.TRUE, r.get("cashCheckOk"), "期初+净流量(0)=期末，应校验通过");
        assertEquals(0, new BigDecimal("0").compareTo((BigDecimal) r.get("cashCheckDiff")));
    }

    @Test
    void p92a_cashFlowStatement_返回全部本年累计字段() {
        // 本期：经营流入 100 / 流出 40
        List<Map<String, Object>> cur = flowRow("OPERATING_IN", 100.00);
        when(reportDataMapper.cashFlowData("202607", "202607")).thenReturn(cur);
        // 本年累计：流入 900 / 流出 300
        List<Map<String, Object>> ytd = flowRow("OPERATING_IN", 900.00);
        when(reportDataMapper.cashFlowData("202601", "202607")).thenReturn(ytd);

        Map<String, Object> cash = new HashMap<>();
        cash.put("begin_cash", 200000.00);
        cash.put("end_cash", 200000.00);
        when(reportDataMapper.cashSubjectBalance("202607")).thenReturn(cash);
        Map<String, Object> cashYtd = new HashMap<>();
        cashYtd.put("begin_cash", 150000.00);
        cashYtd.put("end_cash", 200000.00);
        when(reportDataMapper.cashSubjectBalance("202601")).thenReturn(cashYtd);

        Map<String, Object> r = service.cashFlowStatement("202607");

        // 本期与累计字段同时存在且各自取数（本期流入100无流出 → 净100；累计流入900无流出 → 净900）
        assertEquals(new BigDecimal("100.00"), r.get("operatingIn"));
        assertEquals(new BigDecimal("100.00"), r.get("operatingNet"));
        assertEquals(new BigDecimal("900.00"), r.get("operatingInYtd"));
        assertEquals(new BigDecimal("900.00"), r.get("operatingNetYtd"));
        assertEquals(new BigDecimal("900.00"), r.get("totalNetYtd"));
        // P92-A 口径：累计期初取年初期间(202601)的 begin_cash=150000，而非查询期间的 200000
        assertEquals(new BigDecimal("150000.00"), r.get("openingCashYtd"),
                "本年累计的期初现金必须是年初余额，取查询期间 begin_cash 会漏计年初至当月的现金");
        // 累计期末 = 年初期初 150000 + 累计净流量 900
        assertEquals(new BigDecimal("150900.00"), r.get("closingCashYtd"));
        // 累计期末与本期期末余额勾稽（150900 ≠ 200000，应暴露差异）
        assertEquals(Boolean.FALSE, r.get("cashCheckOkYtd"), "累计期末与科目期末余额不一致必须暴露差异");
    }

    @Test
    void p92a_cashFlowStatement_年初期间本期与累计相等() {
        // 查询期间为年初(1月)时，本期 == 本年累计
        List<Map<String, Object>> rows = flowRow("INVESTING_OUT", 300.00);
        when(reportDataMapper.cashFlowData("202601", "202601")).thenReturn(rows);
        Map<String, Object> cash = new HashMap<>();
        cash.put("begin_cash", 100000.00);
        cash.put("end_cash", 70000.00);
        when(reportDataMapper.cashSubjectBalance("202601")).thenReturn(cash);

        Map<String, Object> r = service.cashFlowStatement("202601");

        assertEquals(r.get("investingOut"), r.get("investingOutYtd"), "年初期间本期与累计应相等");
        assertEquals(r.get("totalNet"), r.get("totalNetYtd"), "年初期间本期与累计净额应相等");
        assertEquals(r.get("openingCash"), r.get("openingCashYtd"), "年初期间期初现金应相等");
        assertEquals(new BigDecimal("-300.00"), r.get("investingNetYtd"));
    }

    @Test
    void p92a_cashFlowStatement_累计与本期互不污染() {
        // 本期为投资流出，累计同时含经营流入——断言各路分类不被串号
        when(reportDataMapper.cashFlowData("202607", "202607"))
                .thenReturn(List.of(flowMap("OPERATING_IN", 100.00), flowMap("OPERATING_OUT", 20.00)));
        when(reportDataMapper.cashFlowData("202601", "202607"))
                .thenReturn(List.of(flowMap("OPERATING_IN", 800.00), flowMap("OPERATING_OUT", 100.00),
                        flowMap("FINANCING_IN", 500.00)));
        Map<String, Object> cash = new HashMap<>();
        cash.put("begin_cash", 1000.00);
        cash.put("end_cash", 1000.00);
        when(reportDataMapper.cashSubjectBalance(anyString())).thenReturn(cash);

        Map<String, Object> r = service.cashFlowStatement("202607");

        assertEquals(new BigDecimal("80.00"), r.get("operatingNet"));
        assertEquals(new BigDecimal("700.00"), r.get("operatingNetYtd"));
        assertEquals(0, new BigDecimal("0").compareTo((BigDecimal) r.get("financingNet")),
                "本期无筹资流量应为 0（compareTo 而非 equals：BigDecimal.equals 连 scale 也比，ZERO 常量为 scale 0）");
        assertEquals(new BigDecimal("500.00"), r.get("financingInYtd"));
        assertEquals(new BigDecimal("1200.00"), r.get("totalNetYtd"), "累计净额=经营净700+筹资净500");
    }

    private static List<Map<String, Object>> flowRow(String type, double amount) {
        return List.of(flowMap(type, amount));
    }

    private static Map<String, Object> flowMap(String type, double amount) {
        Map<String, Object> m = new HashMap<>();
        m.put("flow_type", type);
        m.put("amount", amount);
        return m;
    }
}
