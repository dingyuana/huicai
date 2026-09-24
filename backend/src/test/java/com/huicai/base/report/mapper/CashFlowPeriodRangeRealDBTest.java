package com.huicai.base.report.mapper;

import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * cashFlowData 的期间范围参数化真实 DB 测试（P92-A 本年累计金额）。
 *
 * 为什么必须走真实 DB：本方法是 @Select 注解 SQL，改动点是 WHERE 的期间条件
 * （v.period >= #{startPeriod} AND v.period <= #{endPeriod}）。
 * Mockito 只能验证参数被透传，验证不了 SQL 语义——区间是 >= / <= 还是 = 只有跑真实库才知道。
 * P89-C 的 VoucherPageSubjectIdRealDBTest 已确立同一范式。
 *
 * 同时覆盖 flow_type 分类：P88① 的"按对手方科目区分投资/经营"是 40 行 EXISTS 判定，
 * 之前全部走 mock 从未在真实库执行过，本测试一并验证。
 */
class CashFlowPeriodRangeRealDBTest extends AbstractMapperTest {

    @Autowired
    private ReportDataMapper reportDataMapper;

    @Autowired
    private SubjectMapper subjectMapper;

    @Autowired
    private VoucherMapper voucherMapper;

    @Autowired
    private VoucherEntryMapper voucherEntryMapper;

    private static final long ENT_ID = 9901L;

    private Long insertSubject(String code, String name) {
        Subject s = new Subject();
        s.setCode(code);
        s.setName(name);
        s.setLevel(1);
        s.setDirection("debit");
        s.setIsLeaf(true);
        s.setIsActive(true);
        // Flyway seed 已含 1002/1601 等常用科目，(code, enterprise_id) 唯一约束会冲突，
        // 故用独立 enterpriseId。不能用 code 前缀——会破坏 SQL 里 LIKE '1002%' 与 '15%'~'19%' 的判定。
        s.setEnterpriseId(ENT_ID);
        s.setDeleted(0);
        assertEquals(1, subjectMapper.insert(s));
        return s.getId();
    }

    private VoucherEntity insertVoucher(String period, String suffix) {
        VoucherEntity v = new VoucherEntity();
        v.setVoucherNo("V_CF_" + period + "_" + suffix);
        v.setPeriod(period);
        v.setStatus("POSTED");
        v.setVoucherTypeId(1L);
        v.setEnterpriseId(ENT_ID);
        v.setDeleted(0);
        assertEquals(1, voucherMapper.insert(v));
        return v;
    }

    private void insertEntry(Long voucherId, Long subjectId, String debit, String credit) {
        VoucherEntryEntity e = new VoucherEntryEntity();
        e.setVoucherId(voucherId);
        e.setSubjectId(subjectId);
        e.setDebit(debit == null ? null : new BigDecimal(debit));
        e.setCredit(credit == null ? null : new BigDecimal(credit));
        e.setSummary("现金流期间范围测试");
        e.setSortOrder(1);
        e.setEnterpriseId(ENT_ID);
        e.setDeleted(0);
        assertEquals(1, voucherEntryMapper.insert(e));
    }

    /** 把 mapper 的 (flow_type, amount) 行转成 flow_type -> amount 的 Map 便于断言 */
    private static Map<String, BigDecimal> toMap(List<Map<String, Object>> rows) {
        Map<String, BigDecimal> m = new HashMap<>();
        if (rows != null) {
            for (Map<String, Object> r : rows) {
                m.put((String) r.get("flow_type"), new BigDecimal(r.get("amount").toString()));
            }
        }
        return m;
    }

    private static BigDecimal sum(Map<String, BigDecimal> m) {
        BigDecimal t = BigDecimal.ZERO;
        for (BigDecimal v : m.values()) t = t.add(v);
        return t;
    }

    private static Map<String, BigDecimal> diff(Map<String, BigDecimal> a, Map<String, BigDecimal> b) {
        Map<String, BigDecimal> m = new HashMap<>(b);
        for (Map.Entry<String, BigDecimal> e : a.entrySet()) {
            BigDecimal bv = m.getOrDefault(e.getKey(), BigDecimal.ZERO);
            BigDecimal d = e.getValue().subtract(bv);
            if (d.compareTo(BigDecimal.ZERO) != 0) m.put(e.getKey(), d);
            else m.remove(e.getKey());
        }
        return m;
    }

    @Test
    void 期间范围参数化_本期与累计各自取数() {
        Long bank = insertSubject("1002", "银行存款");
        Long expense = insertSubject("6602", "管理费用");
        Long fixedAsset = insertSubject("1601", "固定资产");

        // 插入前的基线（含 Flyway seed 数据）。测试环境无租户过滤，seed 与本次插入混在同一范围，
        // 绝对值断言必然被 seed 污染，故统一采用「插入前后差值」断言。
        Map<String, BigDecimal> baseCur = toMap(reportDataMapper.cashFlowData("202407", "202407"));
        Map<String, BigDecimal> baseYtd = toMap(reportDataMapper.cashFlowData("202401", "202409"));

        // 202407 三笔：经营流入 1000 / 经营流出 500 / 投资流出 300
        VoucherEntity v1 = insertVoucher("202407", "A");
        insertEntry(v1.getId(), bank, "1000", null);
        insertEntry(v1.getId(), expense, null, "1000");
        VoucherEntity v2 = insertVoucher("202407", "B");
        insertEntry(v2.getId(), bank, null, "500");
        insertEntry(v2.getId(), expense, "500", null);
        VoucherEntity v3 = insertVoucher("202407", "C");
        insertEntry(v3.getId(), bank, null, "300");
        insertEntry(v3.getId(), fixedAsset, "300", null);

        // 202409 一笔经营流入 200（累计范围内、本期范围外）
        VoucherEntity v4 = insertVoucher("202409", "D");
        insertEntry(v4.getId(), bank, "200", null);
        insertEntry(v4.getId(), expense, null, "200");

        // 本期 = [202407, 202407]：只含 202407 的三笔（202409 那笔必须被排除）
        // 注意：diff 出来的 BigDecimal scale=2（源数据金额两位小数），
        // BigDecimal.equals 连 scale 也参与比较，故断言值统一显式 scale(2)。
        Map<String, BigDecimal> cur = diff(baseCur, toMap(reportDataMapper.cashFlowData("202407", "202407")));
        assertEquals(new BigDecimal("1000.00"), cur.get("OPERATING_IN"));
        assertEquals(new BigDecimal("500.00"), cur.get("OPERATING_OUT"));
        assertEquals(new BigDecimal("300.00"), cur.get("INVESTING_OUT"));
        assertFalse(cur.containsKey("INVESTING_IN"), "本期无投资类流入");

        // 本年累计 = [202401, 202409]：含 202407 与 202409 共四笔
        Map<String, BigDecimal> ytd = diff(baseYtd, toMap(reportDataMapper.cashFlowData("202401", "202409")));
        assertEquals(new BigDecimal("1200.00"), ytd.get("OPERATING_IN"), "累计经营流入=1000+200");
        assertEquals(new BigDecimal("500.00"), ytd.get("OPERATING_OUT"));
        assertEquals(new BigDecimal("300.00"), ytd.get("INVESTING_OUT"));

        // 累计 >= 本期，且差额恰为 202409 的 200（期间范围参数化生效的直接证据）
        assertTrue(ytd.get("OPERATING_IN").compareTo(cur.get("OPERATING_IN")) > 0);
        assertEquals(new BigDecimal("200.00"),
                ytd.get("OPERATING_IN").subtract(cur.get("OPERATING_IN")),
                "累计与本期的差额应恰为期间范围内新增的流量");
    }

    @Test
    void 区间上界之外的期间不串入累计() {
        Long bank = insertSubject("1002", "银行存款");
        Long expense = insertSubject("6602", "管理费用");

        // 基线：seed 数据已存在于 [202401,202409]，绝对值断言会被污染
        Map<String, BigDecimal> base = toMap(reportDataMapper.cashFlowData("202401", "202409"));

        // 区间内一笔小额
        VoucherEntity vIn = insertVoucher("202407", "IN");
        insertEntry(vIn.getId(), bank, "100", null);
        insertEntry(vIn.getId(), expense, null, "100");

        // 区间外一笔大额（若期间过滤失效会被整体污染）
        VoucherEntity vOut = insertVoucher("202501", "OUT");
        insertEntry(vOut.getId(), bank, "99999", null);
        insertEntry(vOut.getId(), expense, null, "99999");

        Map<String, BigDecimal> d = diff(base, toMap(reportDataMapper.cashFlowData("202401", "202409")));
        assertEquals(new BigDecimal("100.00"), d.get("OPERATING_IN"),
                "上界 202409 必须排除 202501 的 99999，否则累计口径被污染");
        assertEquals(new BigDecimal("100.00"), sum(d), "区间外流量不得计入累计");

        // 对照组：放宽上界至 202512 后 99999 应被纳入，证明上面的排除确实是期间过滤所致
        Map<String, BigDecimal> dWide = diff(base, toMap(reportDataMapper.cashFlowData("202401", "202512")));
        assertEquals(new BigDecimal("100099.00"), dWide.get("OPERATING_IN"),
                "上界放宽后区间外流量应被纳入，反向证明期间上界生效");
    }

    @Test
    void flow_type分类_投资类与经营类对手方科目正确区分() {
        // 这条测试验证 P88① 的 EXISTS 判定本身：对手方科目在 15%-19% 判投资类，否则经营类。
        // 该判定 40 行、此前全部走 mock 从未在真实库执行，必须用真实数据确认归类方向正确。
        Long bank = insertSubject("1002", "银行存款");
        Long fixedAsset = insertSubject("1601", "固定资产");
        Long intangible = insertSubject("1701", "无形资产");
        Long expense = insertSubject("6602", "管理费用");

        // 基线：Testcontainers 的 seed 与生产库同套件，统一用差值断言避免污染
        Map<String, BigDecimal> base = toMap(reportDataMapper.cashFlowData("202408", "202408"));

        // 购建固定资产：银行付现 → 投资流出
        VoucherEntity vBuy = insertVoucher("202408", "BUY");
        insertEntry(vBuy.getId(), bank, null, "5000");
        insertEntry(vBuy.getId(), fixedAsset, "5000", null);
        // 购入无形资产：银行付现 → 投资流出
        VoucherEntity vIntangible = insertVoucher("202408", "INT");
        insertEntry(vIntangible.getId(), bank, null, "3000");
        insertEntry(vIntangible.getId(), intangible, "3000", null);
        // 取得投资回报：银行收款 → 投资流入
        VoucherEntity vReturn = insertVoucher("202408", "RET");
        insertEntry(vReturn.getId(), bank, "2000", null);
        insertEntry(vReturn.getId(), fixedAsset, null, "2000");
        // 支付费用：银行付现 → 经营流出（对手方 6602 不在 15%-19%）
        VoucherEntity vFee = insertVoucher("202408", "FEE");
        insertEntry(vFee.getId(), bank, null, "100");
        insertEntry(vFee.getId(), expense, "100", null);
        // 费用报销收回：银行收款 → 经营流入
        VoucherEntity vRecover = insertVoucher("202408", "REC");
        insertEntry(vRecover.getId(), bank, "80", null);
        insertEntry(vRecover.getId(), expense, null, "80");

        Map<String, BigDecimal> m = diff(base, toMap(reportDataMapper.cashFlowData("202408", "202408")));

        assertEquals(new BigDecimal("8000.00"), m.get("INVESTING_OUT"), "对手方为固定资产/无形资产应为投资流出");
        assertEquals(new BigDecimal("2000.00"), m.get("INVESTING_IN"), "投资类科目贷方对应银行收款应为投资流入");
        assertEquals(new BigDecimal("100.00"), m.get("OPERATING_OUT"), "对手方为费用科目应为经营流出");
        assertEquals(new BigDecimal("80.00"), m.get("OPERATING_IN"), "非投资类对手方应为经营流入");

        assertEquals(4, m.size(), "本次插入的 5 笔应恰好落在四类 flow_type 上");
        assertEquals(new BigDecimal("10180.00"), sum(m), "本次插入的银行流量合计应全部归类");
    }
}
