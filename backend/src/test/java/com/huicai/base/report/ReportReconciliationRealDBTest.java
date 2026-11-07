package com.huicai.base.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.voucher.service.VoucherService;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * SPEC-P154 切片二：三表勾稽探针（v4.0 §8 —— 报表从未被第三方口径验过）。
 *
 * <p><b>勾稽架构</b>：报表的科目余额读<b>预聚合快照表 {@code t_subject_balance}</b>
 * （过账链 {@code updateBalanceOnPost} 增量维护）——它自己的对错从未被独立口径验过。
 * 本类用<b>独立 SQL 计算器</b>（JdbcTemplate 从 {@code t_voucher_entry} 实时 SUM，
 * 与实现完全不同的代码路径）重算同一份答案，逐科目比对，容差 0.01。</p>
 *
 * <p><b>场景（ent1 / 202612，全链真过账）</b>：期初 1002 银行存款 100,000（借）+
 * 4001 实收资本 100,000（贷）——期初自洽（资产=权益），试算平衡有锚点；
 * V1 收现销售（借 1002 10,000 / 贷 6001 10,000）、V2 付现费用（借 6602 2,000 / 贷 1002 2,000）、
 * V3 期末结转（voucher_no 号段 {@code CLOSE-}，借 6001 10,000 / 贷 6602 2,000 + 贷 4103 8,000）。
 * 期末态手算硬值：1002=108,000；6001=0；6602=0；4103=8,000；净利润=8,000。</p>
 *
 * <p><b>🔴 撞行缺陷（曾登记「报表取数」红线，SPEC-P154 项⑤ 用户裁定修复已落地）</b>：
 * {@code buildBalanceSheet} 的权益组装 {@code if (code.equals("4103")) profit4103 = signed}
 * 按<b>科目编码</b>覆盖赋值，隐含「结果集内 code 唯一」的 RLS 前提。测试容器连接用户是
 * 超级用户（绕 RLS，FORCE 也压不住）时 subject-balance 返回 ent0 参考库 + ent1 两套科目
 * ⇒ ent0 的 4103（余额 0）覆盖 ent1 的 8,000 ⇒ 权益静默缺净利润。
 * <b>已修（2026-11-06）</b>：subjectBalance SQL 带出 {@code s.enterprise_id}，
 * buildBalanceSheet 按 {@code EnterpriseContextHolder} 收敛到本企业行——RLS 正常时恒真
 * no-op，属纵深防御。守卫断言随之从缺陷形态（0 / 100,000）翻面为正确值（8,000 / 108,000）。
 * 同型风险点登记：其余按 {@code code.equals}/{@code code IN} 的取数分支（利润表段位、
 * 现金白名单 1001/1002/1009/1012）在超管绕 RLS 下同样会混企业聚合——利润表 SQL 走
 * t_voucher_entry 实时 SUM 天然带凭证归属，风险面较小，留待专项。</p>
 *
 * <p><b>勾稽断言（全部按 subject_id 对齐——同 code 撞行是上述缺陷的暴露面，
 * 按 id 才是精确勾稽语义）</b>：A 科目余额（快照 vs 分录 SUM 逐科目）；B 资产侧
 * totalAssets=108,000；C 利润表段位（排除 CLOSE-/DISTRIB- 号段）；D 损益清零
 * （6xx 归零 / 4103=8,000）；E 现金流自洽（operatingNet=8,000）。</p>
 *
 * <p><b>反证（F）</b>：改一条分录 +100 后，快照表<b>不漂移</b>（它只随过账链动），
 * 计算器与 API 的差<b>恰为 100</b>——证明两边真在读不同数据源、比对真实生效；
 * 若勾稽实现是「两边都读快照」的恒绿假勾稽，本用例必红。</p>
 */
@DisplayName("SPEC-P154 切片二：独立计算器 vs 报表 API 三表勾稽（容差 0.01）")
@AutoConfigureMockMvc
class ReportReconciliationRealDBTest extends AbstractMapperTest {

    private static final String PERIOD = "202612";
    private static final BigDecimal TOL = new BigDecimal("0.01");

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private VoucherMapper voucherMapper;
    @Autowired private VoucherEntryMapper voucherEntryMapper;
    @Autowired private VoucherService voucherService;
    private final ObjectMapper om = new ObjectMapper();

    private Long subj1002;
    private Long subj4001;
    private Long subj6001;
    private Long subj6602;
    private Long subj4103;
    private Long typeId;

    @BeforeEach
    void seed() {
        EnterpriseContextHolder.set(1L);
        // 期间行（post 链 assertPeriodOpen 要求存在；status=open；opening_status=entered
        // —— 期初已录入语义（chk 约束只认 none/entered/locked），配合下方期初快照直插，
        // 防 validateOpeningBeforePost 拦截）
        jdbcTemplate.update("""
                INSERT INTO t_period(period_code, year, month, start_date, end_date, status, enterprise_id, opening_status)
                VALUES (?, 2026, 12, '2026-12-01', '2026-12-31', 'open', 1, 'entered')
                """, PERIOD);
        subj1002 = subjectId("1002", "银行存款", "debit");
        subj4001 = subjectId("4001", "实收资本", "credit");
        subj6001 = subjectId("6001", "主营业务收入", "credit");
        subj6602 = subjectId("6602", "管理费用", "debit");
        subj4103 = subjectId("4103", "本年利润", "credit");
        // 期初快照（ent1, 202612）：资产 = 权益 自洽
        upsertOpening(subj1002, "100000");
        upsertOpening(subj4001, "100000");

        Map<String, Object> type = jdbcTemplate.queryForMap(
                "SELECT id FROM t_voucher_type WHERE enterprise_id = 1 ORDER BY id LIMIT 1");
        typeId = ((Number) type.get("id")).longValue();

        postVoucher("PROBE-V1", new Object[][] {
                {subj1002, true, "10000"}, {subj6001, false, "10000"}});
        postVoucher("PROBE-V2", new Object[][] {
                {subj6602, true, "2000"}, {subj1002, false, "2000"}});
        // 结转凭证（CLOSE- 号段 → 利润表 SQL 排除；号段即 P137 真实形态）
        postVoucher("CLOSE-202612-PROBE", new Object[][] {
                {subj6001, true, "10000"}, {subj6602, false, "2000"}, {subj4103, false, "8000"}});
    }

    @AfterEach
    void clearCtx() {
        EnterpriseContextHolder.clear();
    }

    // ── 独立 SQL 计算器（不读快照的 end_balance 列，从分录独立推导）──

    /** 末级科目期末余额（独立口径）：期初直插值 + POSTED 分录 SUM，按 direction 定公式。 */
    private BigDecimal calcEndBalance(Long subjectId, String direction) {
        Map<String, Object> r = jdbcTemplate.queryForMap("""
                SELECT COALESCE(sb.begin_balance, 0) AS begin_bal,
                       COALESCE(e.ds, 0) AS ds, COALESCE(e.cs, 0) AS cs
                FROM t_subject s
                LEFT JOIN t_subject_balance sb ON sb.subject_id = s.id AND sb.period = ?
                LEFT JOIN (SELECT ve.subject_id, SUM(ve.debit) ds, SUM(ve.credit) cs
                           FROM t_voucher_entry ve
                           JOIN t_voucher v ON v.id = ve.voucher_id
                           WHERE v.status = 'POSTED' AND v.deleted = 0 AND ve.deleted = 0
                           GROUP BY ve.subject_id) e ON e.subject_id = s.id
                WHERE s.id = ?
                """, PERIOD, subjectId);
        BigDecimal begin = new BigDecimal(r.get("begin_bal").toString());
        BigDecimal ds = new BigDecimal(r.get("ds").toString());
        BigDecimal cs = new BigDecimal(r.get("cs").toString());
        return "debit".equals(direction)
                ? begin.add(ds).subtract(cs)
                : begin.add(cs).subtract(ds);
    }

    // ── 勾稽断言 ──

    @Test
    @DisplayName("勾稽A+B：科目余额逐科目对齐分录 SUM + 资产负债表资产侧与权益缺陷守卫")
    void subjectBalanceAndTrialBalanceReconcile() throws Exception {
        JsonNode data = apiGet("/api/base/report/v1/reports/subject-balance?period=" + PERIOD).path("data");
        // A：按 subject_id 精确对齐（同 code 撞行是 buildBalanceSheet 已登记缺陷的暴露面）
        Map<Long, BigDecimal> expected = new LinkedHashMap<>();
        expected.put(subj1002, calcEndBalance(subj1002, "debit"));   // 108,000
        expected.put(subj4001, calcEndBalance(subj4001, "credit"));  // 100,000
        expected.put(subj6001, calcEndBalance(subj6001, "credit"));  // 0（结转对冲）
        expected.put(subj6602, calcEndBalance(subj6602, "debit"));   // 0（结转对冲）
        expected.put(subj4103, calcEndBalance(subj4103, "credit"));  // 8,000
        for (var en : expected.entrySet()) {
            JsonNode row = rowBySubjectId(data, en.getKey());
            assertNotNull(row, "subject-balance 必须含 subject_id=" + en.getKey() + " 行");
            BigDecimal apiEnd = new BigDecimal(row.path("end_balance").asText());
            assertTrue(apiEnd.subtract(en.getValue()).abs().compareTo(TOL) < 0,
                    "科目 " + row.path("code").asText() + "(id=" + en.getKey() + ") 勾稽失败：快照表 end_balance="
                            + apiEnd + "，独立计算器=" + en.getValue()
                            + " —— 过账链增量维护与分录事实脱钩");
        }

        // B1：资产侧不受撞行影响（各企业 1002 行都进 assets 累加，ent0 行余额 0）
        JsonNode bs = apiGet("/api/base/report/v1/reports/balance-sheet?period=" + PERIOD).path("data");
        assertMoney(bs, "totalAssets", "108000", "资产负债表资产总计");

        // B2：权益缺陷守卫（撞行缺陷已修复——原断言 0/100000 为缺陷形态，现翻面为 8000/108000）
        assertMoney(bs, "currentYearProfit", "8000", "currentYearProfit（撞行已修复，应含 4103 的 8,000）");
        assertMoney(bs, "totalLiabEquity", "108000", "负债权益总计（含 4103 的 8,000）");
    }

    @Test
    @DisplayName("勾稽C+D：利润表段位（排除结转号段）+ 损益清零（6xx 归零 / 4103=净利润）")
    void incomeStatementAndPnlZeroing() throws Exception {
        JsonNode inc = apiGet("/api/base/report/v1/reports/income-statement?period=" + PERIOD).path("data");
        assertMoney(inc, "revenue", "10000", "利润表营业收入段");
        assertMoney(inc, "adminExpense", "2000", "利润表管理费用段");
        assertMoney(inc, "netProfit", "8000", "净利润");

        // D：损益清零 —— 结转过账后 6xx 快照余额必须归零，4103 承接净利润（按 subject_id 对齐）
        JsonNode rows = apiGet("/api/base/report/v1/reports/subject-balance?period=" + PERIOD).path("data");
        assertMoney(rowBySubjectId(rows, subj6001), "end_balance", "0", "损益清零：6001");
        assertMoney(rowBySubjectId(rows, subj6602), "end_balance", "0", "损益清零：6602");
        assertMoney(rowBySubjectId(rows, subj4103), "end_balance", "8000", "损益清零：4103");
    }

    @Test
    @DisplayName("勾稽E：现金流量表经营净额自洽（= 10,000 − 2,000）")
    void cashFlowSelfConsistent() throws Exception {
        JsonNode cf = apiGet("/api/base/report/v1/reports/cash-flow?period=" + PERIOD).path("data");
        assertMoney(cf, "operatingNet", "8000", "现金流量表经营净额");
        assertMoney(cf, "totalNet", "8000", "现金净增加额");
    }

    // ── 反证 F：双真源独立性 ──

    @Test
    @DisplayName("反证F：改分录后快照表不漂移——计算器与 API 差恰为 100（勾稽是双真源比对非恒绿）")
    void calculatorIsActuallyIndependent() throws Exception {
        JsonNode rows = apiGet("/api/base/report/v1/reports/subject-balance?period=" + PERIOD).path("data");
        BigDecimal apiBefore = new BigDecimal(rowBySubjectId(rows, subj1002).path("end_balance").asText());
        // 破坏分录事实（快照表只随过账链动，不随分录 UPDATE 动）
        jdbcTemplate.update(
                "UPDATE t_voucher_entry SET debit = debit + 100 WHERE voucher_id IN "
                        + "(SELECT id FROM t_voucher WHERE voucher_no = 'PROBE-V1') AND subject_id = ?",
                subj1002);
        BigDecimal calcAfter = calcEndBalance(subj1002, "debit");
        JsonNode rowsAfter = apiGet("/api/base/report/v1/reports/subject-balance?period=" + PERIOD).path("data");
        BigDecimal apiAfter = new BigDecimal(rowBySubjectId(rowsAfter, subj1002).path("end_balance").asText());
        assertEquals(0, apiAfter.compareTo(apiBefore),
                "快照表不应随分录 UPDATE 漂移——若漂移说明断言探错了对象");
        assertTrue(calcAfter.subtract(apiAfter).abs().compareTo(new BigDecimal("100")) < TOL.doubleValue(),
                "计算器与 API 的差应恰为 100（分录 +100，快照未动）——实际差 "
                        + calcAfter.subtract(apiAfter)
                        + "。若为 0 说明勾稽是「两边读同源」的恒绿假勾稽，反证失效");
    }

    // ── 造数与断言工具 ──

    private JsonNode rowBySubjectId(JsonNode rows, Long subjectId) {
        for (JsonNode row : rows) {
            if (row.path("id").asLong() == subjectId) {
                return row;
            }
        }
        return null;
    }

    private void assertMoney(JsonNode node, String field, String expected, String label) {
        BigDecimal actual = new BigDecimal(node.path(field).asText("NaN"));
        assertTrue(actual.subtract(new BigDecimal(expected)).abs().compareTo(TOL) < 0,
                label + " 勾稽失败：期望≈" + expected + "，实际=" + actual);
    }

    private Long subjectId(String code, String name, String direction) {
        var ids = jdbcTemplate.queryForList(
                "SELECT id FROM t_subject WHERE code = ? AND enterprise_id = 1 AND deleted = 0",
                Long.class, code);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        jdbcTemplate.update("""
                INSERT INTO t_subject(code, name, direction, is_leaf, enterprise_id, level)
                VALUES (?, ?, ?, TRUE, 1, 1)
                """, code, name, direction);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM t_subject WHERE code = ? AND enterprise_id = 1 AND deleted = 0 "
                        + "ORDER BY id DESC LIMIT 1", Long.class, code);
    }

    private void upsertOpening(Long subjectId, String balance) {
        jdbcTemplate.update("DELETE FROM t_subject_balance WHERE subject_id = ? AND period = ?",
                subjectId, PERIOD);
        jdbcTemplate.update("""
                INSERT INTO t_subject_balance(subject_id, year, period, begin_balance, end_balance)
                VALUES (?, '2026', ?, ?, ?)
                """, subjectId, PERIOD, new BigDecimal(balance), new BigDecimal(balance));
    }

    private void postVoucher(String no, Object[][] lines) {
        VoucherEntity v = new VoucherEntity();
        v.setVoucherNo(no);
        v.setPeriod(PERIOD);
        v.setVoucherTypeId(typeId);
        v.setStatus("AUDITED");
        voucherMapper.insert(v);
        BigDecimal total = BigDecimal.ZERO;
        for (Object[] line : lines) {
            VoucherEntryEntity e = new VoucherEntryEntity();
            e.setVoucherId(v.getId());
            e.setSubjectId((Long) line[0]);
            BigDecimal amount = new BigDecimal((String) line[2]);
            if ((Boolean) line[1]) {
                e.setDebit(amount);
            } else {
                e.setCredit(amount);
            }
            voucherEntryMapper.insert(e);
            total = total.add(amount);
        }
        v.setTotalDebit(total);
        v.setTotalCredit(total);
        voucherMapper.updateById(v);
        voucherService.post(v.getId(), 1L);
    }

    /**
     * 🔴 必须逐请求重设上下文：JwtAuthenticationFilter 的 finally 无条件
     * {@code EnterpriseContextHolder.clear()}，MockMvc 与测试同线程 ⇒ 首个 apiGet 返回后
     * @BeforeEach 设的 ctx 已被抹掉。生产由 JWT 逐请求重建上下文，这里按同构模拟
     * （撞行修复的租户收敛逻辑依赖 ctx 存在，ctx=null 时刻意不猜归属）。
     */
    private JsonNode apiGet(String url) throws Exception {
        EnterpriseContextHolder.set(1L);
        var result = mockMvc.perform(get(url)
                        .with(SecurityMockMvcRequestPostProcessors.user("probe")
                                .authorities(() -> "report:view")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode root = om.readTree(body);
        assertEquals(200, root.path("code").asInt(), "R 信封必须成功：" + url);
        assertNotNull(root.path("data"), "data 不得为空：" + url);
        return root;
    }
}
