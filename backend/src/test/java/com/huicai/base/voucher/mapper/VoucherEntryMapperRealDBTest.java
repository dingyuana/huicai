package com.huicai.base.voucher.mapper;

import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.voucher.dto.AuxiliarySummaryRow;
import com.huicai.base.voucher.dto.LedgerEntryRowDTO;
import com.huicai.base.voucher.dto.SubjectProfitTotalRow;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VoucherEntryMapper.selectBySubjectIdAndPeriod 真实 DB 测试.
 *
 * 核心断言：账簿查询必须按会计期间(period)过滤分录。
 * 修复前该查询只按 subjectId 过滤，导致跨期间数据串账。
 */
class VoucherEntryMapperRealDBTest extends AbstractMapperTest {

    @Autowired
    private VoucherEntryMapper voucherEntryMapper;

    @Autowired
    private VoucherMapper voucherMapper;

    @Autowired
    private SubjectMapper subjectMapper;

    private Long insertSubject(int code) {
        Subject s = new Subject();
        s.setCode("ZTEST_" + code);
        s.setName("测试科目" + code);
        s.setLevel(1);
        s.setDirection("debit");
        s.setIsLeaf(true);
        s.setIsActive(true);
        s.setEnterpriseId(1L);
        s.setDeleted(0);
        assertEquals(1, subjectMapper.insert(s));
        return s.getId();
    }

    private VoucherEntity insertVoucher(String period) {
        VoucherEntity v = new VoucherEntity();
        v.setVoucherNo("V_" + period + "_" + System.nanoTime());
        v.setPeriod(period);
        v.setStatus("POSTED");
        v.setVoucherTypeId(1L);
        v.setEnterpriseId(1L);
        v.setDeleted(0);
        assertEquals(1, voucherMapper.insert(v));
        assertNotNull(v.getId());
        return v;
    }

    private VoucherEntryEntity insertEntry(Long voucherId, Long subjectId, String debit, String credit) {
        VoucherEntryEntity e = new VoucherEntryEntity();
        e.setVoucherId(voucherId);
        e.setSubjectId(subjectId);
        e.setDebit(debit == null ? null : new BigDecimal(debit));
        e.setCredit(credit == null ? null : new BigDecimal(credit));
        e.setSummary("测试分录");
        e.setSortOrder(1);
        e.setEnterpriseId(1L);
        e.setDeleted(0);
        assertEquals(1, voucherEntryMapper.insert(e));
        return e;
    }

    private VoucherEntryEntity insertEntryWithAssist(Long voucherId, Long subjectId, String debit, String credit, String assistJson) {
        VoucherEntryEntity e = new VoucherEntryEntity();
        e.setVoucherId(voucherId);
        e.setSubjectId(subjectId);
        e.setDebit(debit == null ? null : new BigDecimal(debit));
        e.setCredit(credit == null ? null : new BigDecimal(credit));
        e.setSummary("测试分录");
        e.setAssistJson(assistJson);
        e.setSortOrder(1);
        e.setEnterpriseId(1L);
        e.setDeleted(0);
        assertEquals(1, voucherEntryMapper.insert(e));
        return e;
    }

    @Test
    void selectBySubjectIdAndPeriod_只返回指定期间分录() {
        Long subjectId = insertSubject(1);
        String targetPeriod = "202607";
        String otherPeriod = "202606";

        // 目标期间：2 笔分录
        VoucherEntity v1 = insertVoucher(targetPeriod);
        insertEntry(v1.getId(), subjectId, "1000", null);
        VoucherEntity v2 = insertVoucher(targetPeriod);
        insertEntry(v2.getId(), subjectId, "2000", null);

        // 其他期间：1 笔分录（不应出现在结果中）
        VoucherEntity vOther = insertVoucher(otherPeriod);
        insertEntry(vOther.getId(), subjectId, "9999", null);

        List<VoucherEntryEntity> result = voucherEntryMapper.selectBySubjectIdAndPeriod(subjectId, targetPeriod);

        assertEquals(2, result.size(), "只应返回 202607 期间的 2 笔分录");
        assertTrue(result.stream().allMatch(e -> e.getSubjectId().equals(subjectId)));
        assertTrue(result.stream().noneMatch(e -> e.getDebit() != null && e.getDebit().compareTo(new BigDecimal("9999")) == 0),
                "其他期间的 9999 分录不应出现在结果中");
    }

    @Test
    void selectBySubjectIdAndPeriod_排除已删除凭证() {
        Long subjectId = insertSubject(2);
        String period = "202607";

        // 正常凭证 + 分录
        VoucherEntity v1 = insertVoucher(period);
        insertEntry(v1.getId(), subjectId, "500", null);

        // 已删除凭证（deleted=1）的分录不应返回
        VoucherEntity vDel = insertVoucher(period);
        insertEntry(vDel.getId(), subjectId, "8888", null);
        voucherMapper.deleteById(vDel.getId());  // @TableLogic 逻辑删除 → deleted=1

        List<VoucherEntryEntity> result = voucherEntryMapper.selectBySubjectIdAndPeriod(subjectId, period);

        assertEquals(1, result.size(), "已删除凭证的分录不应返回");
    }

    @Test
    void selectBySubjectIdAndPeriod_期间无分录返回空() {
        Long subjectId = insertSubject(3);
        List<VoucherEntryEntity> result = voucherEntryMapper.selectBySubjectIdAndPeriod(subjectId, "199901");
        assertTrue(result.isEmpty(), "无分录期间应返回空列表");
    }

    @Test
    void selectSubsidiaryByDates_日期范围过滤生效() {
        Long subjectId = insertSubject(4);
        String period = "202608";

        VoucherEntity vEarly = insertVoucherWithDate(period, java.time.LocalDate.of(2026, 8, 1));
        insertEntry(vEarly.getId(), subjectId, "100", null);
        VoucherEntity vLate = insertVoucherWithDate(period, java.time.LocalDate.of(2026, 8, 20));
        insertEntry(vLate.getId(), subjectId, "200", null);

        List<VoucherEntryEntity> ranged = voucherEntryMapper.selectSubsidiaryByDates(
                subjectId, period, java.time.LocalDate.of(2026, 8, 5), java.time.LocalDate.of(2026, 8, 31));

        assertEquals(1, ranged.size(), "日期范围 8/5-8/31 应只返回 8/20 的分录");
        assertEquals(0, new BigDecimal("200.00").compareTo(ranged.get(0).getDebit()));

        List<VoucherEntryEntity> all = voucherEntryMapper.selectSubsidiaryByDates(subjectId, period, null, null);
        assertEquals(2, all.size(), "日期为 null 时退化为期间过滤，应返回全部分录");
    }

    private VoucherEntity insertVoucherWithDate(String period, java.time.LocalDate date) {
        VoucherEntity v = insertVoucher(period);
        VoucherEntity update = new VoucherEntity();
        update.setId(v.getId());
        update.setCreatedAt(date.atStartOfDay());
        voucherMapper.updateById(update);
        return v;
    }

    private VoucherEntity insertVoucherWithStatus(String period, String status) {
        VoucherEntity v = insertVoucher(period);
        VoucherEntity update = new VoucherEntity();
        update.setId(v.getId());
        update.setStatus(status);
        voucherMapper.updateById(update);
        return v;
    }

    @Test
    void selectSubsidiaryRows_返回投影含voucherNo默认只含POSTED() {
        Long subjectId = insertSubject(5);
        String period = "202608";

        VoucherEntity posted = insertVoucherWithStatus(period, "POSTED");
        insertEntry(posted.getId(), subjectId, "500", null);
        VoucherEntity draft = insertVoucherWithStatus(period, "DRAFT");
        insertEntry(draft.getId(), subjectId, "800", null);

        List<LedgerEntryRowDTO> defaultRows =
                voucherEntryMapper.selectSubsidiaryRows(subjectId, period, null, null, false);

        assertEquals(1, defaultRows.size(), "默认只返回 POSTED 凭证分录 (T8)");
        assertEquals(0, new BigDecimal("500.00").compareTo(defaultRows.get(0).getDebit()));
        assertNotNull(defaultRows.get(0).getVoucherNo(), "投影应含 voucherNo");
        assertNotNull(defaultRows.get(0).getVoucherDate(), "投影应含 voucherDate");
        assertEquals(posted.getVoucherNo(), defaultRows.get(0).getVoucherNo(), "voucherNo 应与凭证一致");

        List<LedgerEntryRowDTO> allRows =
                voucherEntryMapper.selectSubsidiaryRows(subjectId, period, null, null, true);

        assertEquals(2, allRows.size(), "includeUnposted=true 返回全部状态分录");
    }

    // ─── 辅助核算账聚合 ───────────────────────────────────────────────────

    @Test
    void selectAuxiliaryMovement_按维度值过滤并按科目汇总() {
        Long subjectId = insertSubject(10);
        String period = "202608";
        VoucherEntity v1 = insertVoucher(period);
        insertEntryWithAssist(v1.getId(), subjectId, "500", null, "{\"customerId\":1001}");
        insertEntryWithAssist(v1.getId(), subjectId, "200", null, "{\"customerId\":1001}");
        insertEntryWithAssist(v1.getId(), subjectId, "300", null, "{\"customerId\":2001}");

        List<AuxiliarySummaryRow> rows =
                voucherEntryMapper.selectAuxiliaryMovement("customerId", 1001L, period);

        assertEquals(1, rows.size(), "只应返回 customerId=1001 的聚合行");
        assertEquals(0, new BigDecimal("700.00").compareTo(rows.get(0).getDebitTotal()), "借方合计=500+200=700");
        assertEquals("1001", rows.get(0).getDimensionValue());
    }

    @Test
    void selectAuxiliaryMovement_dimensionValue为空_按维度值分组() {
        Long subjectId = insertSubject(11);
        String period = "202608";
        VoucherEntity v1 = insertVoucher(period);
        insertEntryWithAssist(v1.getId(), subjectId, "500", null, "{\"customerId\":1001}");
        insertEntryWithAssist(v1.getId(), subjectId, "300", null, "{\"customerId\":2001}");

        List<AuxiliarySummaryRow> rows =
                voucherEntryMapper.selectAuxiliaryMovement("customerId", null, period);

        assertEquals(2, rows.size(), "应按维度值分组返回 2 行");
    }

    @Test
    void selectAuxiliaryOpening_只聚合历史期间() {
        Long subjectId = insertSubject(12);
        String targetPeriod = "202608";
        VoucherEntity vPrev = insertVoucher("202607");
        insertEntryWithAssist(vPrev.getId(), subjectId, "100", null, "{\"customerId\":1001}");
        insertEntryWithAssist(vPrev.getId(), subjectId, "0", "50", "{\"customerId\":1001}");
        VoucherEntity vCur = insertVoucher(targetPeriod);
        insertEntryWithAssist(vCur.getId(), subjectId, "999", null, "{\"customerId\":1001}");

        List<AuxiliarySummaryRow> rows =
                voucherEntryMapper.selectAuxiliaryOpening("customerId", 1001L, targetPeriod);

        assertEquals(1, rows.size(), "期初聚合只含历史期间(202607)");
        assertEquals(0, new BigDecimal("100.00").compareTo(rows.get(0).getDebitTotal()), "期初借方=100");
        assertEquals(0, new BigDecimal("50.00").compareTo(rows.get(0).getCreditTotal()), "期初贷方=50");
    }

    // ─── P85 损益科目聚合（结转凭证生成用）──────────────────────────────────

    /** 造一个真实 6xx 损益科目（前缀 insertSubject 用 ZTEST_ 不匹配 LIKE '6%'，故单独造） */
    private Long insertProfitSubject(String code, String name, String direction) {
        Subject s = new Subject();
        s.setCode(code);
        s.setName(name);
        s.setLevel(1);
        s.setDirection(direction);
        s.setIsLeaf(true);
        s.setIsActive(true);
        s.setEnterpriseId(1L);
        s.setDeleted(0);
        assertEquals(1, subjectMapper.insert(s));
        return s.getId();
    }

    @Test
    void selectProfitSubjectTotals_按6xx聚合借贷且排除非损益与零值() {
        String period = "202608";

        // 6701 管理费用(debit): 2 笔已记账凭证 → 借方合计 1500
        Long expenseId = insertProfitSubject("6701", "管理费用", "debit");
        VoucherEntity v1 = insertVoucher(period);
        insertEntry(v1.getId(), expenseId, "1000", null);
        VoucherEntity v2 = insertVoucher(period);
        insertEntry(v2.getId(), expenseId, "500", null);

        // 6801 主营业务收入(credit): 1 笔 → 贷方合计 2000
        Long revenueId = insertProfitSubject("6801", "主营业务收入", "credit");
        VoucherEntity v3 = insertVoucher(period);
        insertEntry(v3.getId(), revenueId, null, "2000");

        // 6603 财务费用(debit): 借贷对冲净额为 0 → HAVING 应过滤掉
        Long offsetId = insertProfitSubject("6603", "财务费用", "debit");
        VoucherEntity v4 = insertVoucher(period);
        insertEntry(v4.getId(), offsetId, "300", null);
        insertEntry(v4.getId(), offsetId, null, "300");

        // 6604 制造费用: 借贷对冲为 0 的 6xx 科目 → HAVING 也应过滤掉
        Long zeroId = insertProfitSubject("6604", "制造费用", "debit");
        VoucherEntity v5 = insertVoucher(period);
        insertEntry(v5.getId(), zeroId, "500", null);
        insertEntry(v5.getId(), zeroId, null, "500");

        // 非 6xx 科目(1901 银行存款)：不应匹配 LIKE '6%'
        Long bankId = insertProfitSubject("1901", "银行存款", "debit");
        VoucherEntity v6 = insertVoucher(period);
        insertEntry(v6.getId(), bankId, "3000", null);

        // 6701 的另一期间(202607)分录：不应混入
        VoucherEntity vOther = insertVoucher("202607");
        insertEntry(vOther.getId(), expenseId, "7777", null);

        // 6701 的 DRAFT 凭证分录：未记账，不应计入
        VoucherEntity vDraft = insertVoucherWithStatus(period, "DRAFT");
        insertEntry(vDraft.getId(), expenseId, "8888", null);

        // 6701 的已删除凭证分录：不应计入
        VoucherEntity vDel = insertVoucher(period);
        insertEntry(vDel.getId(), expenseId, "9999", null);
        voucherMapper.deleteById(vDel.getId());

        List<SubjectProfitTotalRow> rows = voucherEntryMapper.selectProfitSubjectTotals(period);

        assertEquals(2, rows.size(), "只应返回 6701(借1500) 与 6801(贷2000)，0 值/非6xx/其他期间/未记账/已删除均应排除");
        Map<String, SubjectProfitTotalRow> byCode = rows.stream()
                .collect(Collectors.toMap(SubjectProfitTotalRow::getCode, Function.identity()));
        assertTrue(byCode.containsKey("6701"), "应含 6701");
        assertTrue(byCode.containsKey("6801"), "应含 6801");
        assertFalse(byCode.containsKey("6603"), "借贷对冲为 0 的 6603 不应出现");
        assertFalse(byCode.containsKey("6604"), "借贷对冲为 0 的 6604 不应出现");
        assertFalse(byCode.containsKey("1901"), "非 6xx 的 1901 不应出现");

        SubjectProfitTotalRow expenseRow = byCode.get("6701");
        assertEquals(0, new BigDecimal("1500.00").compareTo(expenseRow.getDebitTotal()), "6701 借方=1000+500=1500（不含其他期间7777/未记账8888/已删除9999）");
        assertEquals(0, BigDecimal.ZERO.compareTo(expenseRow.getCreditTotal()), "6701 贷方=0");
        assertEquals("debit", expenseRow.getDirection());
        assertEquals("管理费用", expenseRow.getName());

        SubjectProfitTotalRow revenueRow = byCode.get("6801");
        assertEquals(0, new BigDecimal("2000.00").compareTo(revenueRow.getCreditTotal()), "6801 贷方=2000");
        assertEquals(0, BigDecimal.ZERO.compareTo(revenueRow.getDebitTotal()), "6801 借方=0");
        assertEquals("credit", revenueRow.getDirection());
    }

    @Test
    void selectProfitSubjectTotals_期间无损益科目返回空() {
        // 该期间只有非 6xx 科目分录（1901 与种子数据无重码冲突）
        Long bankId = insertProfitSubject("1901", "银行存款", "debit");
        VoucherEntity v = insertVoucher("202609");
        insertEntry(v.getId(), bankId, "100", null);

        List<SubjectProfitTotalRow> rows = voucherEntryMapper.selectProfitSubjectTotals("202609");
        assertTrue(rows.isEmpty(), "无 6xx 损益科目的期间应返回空列表");
    }
}
