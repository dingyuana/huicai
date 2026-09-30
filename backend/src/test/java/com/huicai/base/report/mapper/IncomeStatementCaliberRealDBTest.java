package com.huicai.base.report.mapper;

import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * P97 阶段 A / REQ-099：利润表取数口径真实 DB 测试。
 *
 * 为什么必须真实 DB：本批改的是 {@code incomeStatementData} 的 SQL 段位划分。
 * dev 数据 6xx 几乎全零（仅 6603 有余额行且为 0.00），页面上看不出任何问题，
 * Mock 更只能验证参数透传——口径错在哪一段、漏了哪一段，只有跑真实库才看得见。
 *
 * 旧口径三处缺陷（本次要钉死的负向断言）：
 *  1. revenue = direction='credit' AND code LIKE '6%' → 营业外收入/其他收益/投资收益被算进营业收入
 *  2. 6403 税金及附加整段缺失 → 利润总额虚高
 *  3. 6801 所得税费用缺失 → 无净利润
 */
class IncomeStatementCaliberRealDBTest extends AbstractMapperTest {

    private static final long ENT_ID = 9904L;
    private static final String PERIOD = "202403";

    @Autowired
    private ReportDataMapper reportDataMapper;

    @Autowired
    private SubjectMapper subjectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Flyway seed 用显式 id 插入，identity sequence 未被推进，而 sequence 不受事务回滚影响——
     * 真实 DB 测试生成的 id 会与 seed 行撞主键（DuplicateKeyException: t_subject_pkey）。
     * 故每次测试前把序列对齐到当前最大 id，使测试可重复运行。
     */
    @BeforeEach
    void alignIdentitySequences() {
        // 本类用独立企业 ENT_ID 造数，数据权限拦截器按上下文过滤，
        // 故上下文须与造数企业一致，否则报表查询返回 0 行
        useEnterprise(ENT_ID);
        align("t_subject_id_seq", "t_subject");
        align("t_voucher_id_seq", "t_voucher");
        align("t_voucher_entry_id_seq", "t_voucher_entry");
    }

    private void align(String seq, String table) {
        jdbcTemplate.execute("SELECT setval('" + seq + "', GREATEST((SELECT COALESCE(MAX(id), 1) FROM "
                + table + "), 1))");
    }


    @Autowired
    private VoucherMapper voucherMapper;

    @Autowired
    private VoucherEntryMapper voucherEntryMapper;

    private Long insertSubject(String code, String name, String direction) {
        Subject s = new Subject();
        s.setCode(code);
        s.setName(name);
        s.setLevel(1);
        s.setDirection(direction);
        s.setIsLeaf(true);
        s.setIsActive(true);
        s.setEnterpriseId(ENT_ID);
        s.setDeleted(0);
        assertEquals(1, subjectMapper.insert(s));
        return s.getId();
    }

    private VoucherEntity insertVoucher(String suffix) {
        VoucherEntity v = new VoucherEntity();
        v.setVoucherNo("V_IS_" + suffix);
        v.setPeriod(PERIOD);
        v.setStatus("POSTED");
        v.setVoucherTypeId(1L);
        v.setEnterpriseId(ENT_ID);
        v.setDeleted(0);
        assertEquals(1, voucherMapper.insert(v));
        return v;
    }

    private void entry(Long voucherId, Long subjectId, String debit, String credit) {
        VoucherEntryEntity e = new VoucherEntryEntity();
        e.setVoucherId(voucherId);
        e.setSubjectId(subjectId);
        e.setDebit(debit == null ? null : new BigDecimal(debit));
        e.setCredit(credit == null ? null : new BigDecimal(credit));
        e.setSummary("利润表口径测试");
        e.setSortOrder(1);
        e.setEnterpriseId(ENT_ID);
        e.setDeleted(0);
        assertEquals(1, voucherEntryMapper.insert(e));
    }

    private BigDecimal seg(String key) {
        Map<String, Object> row = reportDataMapper.incomeStatementData(PERIOD);
        assertNotNull(row, "incomeStatementData 必须返回一行");
        Object v = row.get(key);
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
    }

    @Test
    void 营业收入只含主营与其他业务收入() {
        Long main = insertSubject("6001", "主营业务收入", "credit");
        Long otherBiz = insertSubject("6051", "其他业务收入", "credit");
        // 营业外收入贷方 5000：旧口径会被算进营业收入
        Long nonOpIncome = insertSubject("6301", "营业外收入", "credit");
        // 其他收益贷方 3000：旧口径同样会被算进营业收入
        Long otherIncome = insertSubject("6117", "其他收益", "credit");
        // 投资收益贷方 2000
        Long investIncome = insertSubject("6111", "投资收益", "credit");

        VoucherEntity v = insertVoucher("REV");
        entry(v.getId(), main, null, "10000");
        entry(v.getId(), nonOpIncome, null, "5000");
        entry(v.getId(), otherIncome, null, "3000");
        entry(v.getId(), investIncome, null, "2000");
        VoucherEntity v2 = insertVoucher("REV2");
        entry(v2.getId(), otherBiz, null, "2000");

        assertEquals(0, new BigDecimal("12000").compareTo(seg("revenue")),
                "营业收入只含 6001+6051 = 12000，不得把 6301/6117/6111 的 10000 算进来");
        assertEquals(0, new BigDecimal("5000").compareTo(seg("non_operating_income")),
                "营业外收入必须独立成行");
        assertEquals(0, new BigDecimal("3000").compareTo(seg("other_income")),
                "其他收益必须独立成行");
        assertEquals(0, new BigDecimal("2000").compareTo(seg("investment_income")),
                "投资收益必须独立成行");
    }

    @Test
    void 税金及附加单独取数_不再整段缺失() {
        Long tax = insertSubject("6403", "税金及附加", "debit");
        VoucherEntity v = insertVoucher("TAX");
        entry(v.getId(), tax, "1500", null);

        assertEquals(0, new BigDecimal("1500").compareTo(seg("tax_and_surcharge")),
                "6403 税金及附加必须单独取数（旧口径整段缺失，利润总额虚高）");
    }

    @Test
    void 期间费用四费分列且不重不漏() {
        Long selling = insertSubject("6601", "销售费用", "debit");
        Long admin = insertSubject("6602", "管理费用", "debit");
        Long financial = insertSubject("6603", "财务费用", "debit");
        Long rd = insertSubject("6604", "研发费用", "debit");

        VoucherEntity v = insertVoucher("EXP");
        entry(v.getId(), selling, "1000", null);
        entry(v.getId(), admin, "2000", null);
        entry(v.getId(), financial, "300", null);
        entry(v.getId(), rd, "4000", null);

        assertEquals(0, new BigDecimal("1000").compareTo(seg("selling_expense")), "销售费用");
        assertEquals(0, new BigDecimal("2000").compareTo(seg("admin_expense")), "管理费用");
        assertEquals(0, new BigDecimal("300").compareTo(seg("financial_expense")), "财务费用");
        assertEquals(0, new BigDecimal("4000").compareTo(seg("rd_expense")),
                "研发费用必须独立（旧口径被错列进「其他支出」）");
        BigDecimal fourSum = seg("selling_expense").add(seg("admin_expense"))
                .add(seg("financial_expense")).add(seg("rd_expense"));
        assertEquals(0, new BigDecimal("7300").compareTo(fourSum),
                "负向：四费合计必须等于 7300（SQL 侧逐段独立，不得互相吞并）");
    }

    @Test
    void 营业外支出与所得税分别取数() {
        Long nonOpExpense = insertSubject("6711", "营业外支出", "debit");
        Long incomeTax = insertSubject("6801", "所得税费用", "debit");

        VoucherEntity v = insertVoucher("TAXNOP");
        entry(v.getId(), nonOpExpense, "700", null);
        entry(v.getId(), incomeTax, "2500", null);

        assertEquals(0, new BigDecimal("700").compareTo(seg("non_operating_expense")),
                "营业外支出必须独立（旧口径与研发费用混在 other_expense）");
        assertEquals(0, new BigDecimal("2500").compareTo(seg("income_tax")),
                "所得税费用必须取数（旧口径缺失，报表无净利润）");
    }
}
