package com.huicai.base.voucher.mapper;

import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.voucher.dto.AuxiliarySummaryRow;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

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
}
