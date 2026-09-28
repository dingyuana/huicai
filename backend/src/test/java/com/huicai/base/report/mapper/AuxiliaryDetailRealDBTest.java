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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P97 阶段 C-2 / REQ-097：辅助核算明细聚合。
 *
 * 为什么按整个 assist_json 值分组、而不按 customerName 之类的具体键取值：
 * 本项目 assist_json 全链路透传（前端 api/voucher.ts、业务单据转换、VoucherServiceImpl 都是
 * `setAssistJson(上游值)`），**没有任何一处定义其 schema**。按臆测键名写 SQL 会匹配不到
 * 任何真实数据，且一旦上游键名不同就静默返回空——正是本次要避免的失败模式。
 * 故按 jsonb 值整体分组、原样返回，由前端通用渲染（键名对上游透明）。
 */
class AuxiliaryDetailRealDBTest extends AbstractMapperTest {

    private static final long ENT_ID = 9905L;
    private static final String PERIOD = "202403";

    @Autowired
    private ReportDataMapper reportDataMapper;

    @Autowired
    private SubjectMapper subjectMapper;

    @Autowired
    private VoucherMapper voucherMapper;

    @Autowired
    private VoucherEntryMapper voucherEntryMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void alignIdentitySequences() {
        align("t_subject_id_seq", "t_subject");
        align("t_voucher_id_seq", "t_voucher");
        align("t_voucher_entry_id_seq", "t_voucher_entry");
    }

    private void align(String seq, String table) {
        jdbcTemplate.execute("SELECT setval('" + seq + "', GREATEST((SELECT COALESCE(MAX(id), 1) FROM "
                + table + "), 1))");
    }

    private Long insertSubject(String code, String name, String direction, String auxType) {
        Subject s = new Subject();
        s.setCode(code);
        s.setName(name);
        s.setLevel(1);
        s.setDirection(direction);
        s.setIsLeaf(true);
        s.setIsActive(true);
        s.setEnterpriseId(ENT_ID);
        s.setDeleted(0);
        if (auxType != null) {
            s.setAuxCalcType(auxType);
        }
        assertEquals(1, subjectMapper.insert(s));
        return s.getId();
    }

    private VoucherEntity insertVoucher(String suffix) {
        VoucherEntity v = new VoucherEntity();
        v.setVoucherNo("V_AUX_" + suffix);
        v.setPeriod(PERIOD);
        v.setStatus("POSTED");
        v.setVoucherTypeId(1L);
        v.setEnterpriseId(ENT_ID);
        v.setDeleted(0);
        assertEquals(1, voucherMapper.insert(v));
        return v;
    }

    private void entry(Long voucherId, Long subjectId, String debit, String credit, String assistJson) {
        VoucherEntryEntity e = new VoucherEntryEntity();
        e.setVoucherId(voucherId);
        e.setSubjectId(subjectId);
        e.setDebit(debit == null ? null : new BigDecimal(debit));
        e.setCredit(credit == null ? null : new BigDecimal(credit));
        e.setSummary("辅助核算测试");
        e.setAssistJson(assistJson);
        e.setSortOrder(1);
        e.setEnterpriseId(ENT_ID);
        e.setDeleted(0);
        assertEquals(1, voucherEntryMapper.insert(e));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> auxOf(String period) {
        List<Map<String, Object>> rows = reportDataMapper.auxiliaryMovement(period);
        return rows == null ? List.of() : rows;
    }

    @Test
    void 同一辅助项的多笔分录合并为一行() {
        Long prepaid = insertSubject("1123", "预付账款", "debit", "vendor");
        VoucherEntity v1 = insertVoucher("A");
        entry(v1.getId(), prepaid, "1000", null, "{\"vendorId\":11,\"vendorName\":\"甲供应商\"}");
        VoucherEntity v2 = insertVoucher("B");
        entry(v2.getId(), prepaid, "500", null, "{\"vendorId\":11,\"vendorName\":\"甲供应商\"}");

        List<Map<String, Object>> rows = auxOf(PERIOD);

        assertEquals(1, rows.size(), "同一 vendorId 的两笔必须合并为一行");
        Map<String, Object> r = rows.get(0);
        assertEquals("1123", r.get("subject_code"));
        assertEquals("vendor", r.get("aux_calc_type"));
        assertEquals(0, new BigDecimal("1500").compareTo(new BigDecimal(r.get("debit_total").toString())),
                "甲供应商本期借方合计 1500");
    }

    @Test
    void 不同辅助项分行且各自独立() {
        Long prepaid = insertSubject("1123", "预付账款", "debit", "vendor");
        VoucherEntity v = insertVoucher("C");
        entry(v.getId(), prepaid, "1000", null, "{\"vendorId\":11,\"vendorName\":\"甲供应商\"}");
        entry(v.getId(), prepaid, "700", null, "{\"vendorId\":22,\"vendorName\":\"乙供应商\"}");

        List<Map<String, Object>> rows = auxOf(PERIOD);

        assertEquals(2, rows.size(), "两个辅助项必须各占一行");
        assertTrue(rows.stream().allMatch(r -> "1123".equals(r.get("subject_code"))));
    }

    @Test
    void 负向_未配辅助核算的科目不进入明细() {
        Long bank = insertSubject("1002", "银行存款", "debit", null);
        VoucherEntity v = insertVoucher("D");
        entry(v.getId(), bank, "9999", null, "{\"vendorId\":11}");

        assertEquals(0, auxOf(PERIOD).size(),
                "负向：科目未配 aux_calc_type 时不进辅助核算明细（否则明细之和会与科目合计对不上）");
    }

    @Test
    void 分录无辅助项时不计入明细() {
        Long prepaid = insertSubject("1123", "预付账款", "debit", "vendor");
        VoucherEntity v = insertVoucher("E");
        entry(v.getId(), prepaid, "300", null, null);

        assertEquals(0, auxOf(PERIOD).size(),
                "负向：assist_json 为空的分录无法归入某个辅助项，不进明细（避免凭空归集）");
    }

    @Test
    void 累计列覆盖年初至本期() {
        Long prepaid = insertSubject("1123", "预付账款", "debit", "vendor");
        VoucherEntity v = insertVoucher("F");
        entry(v.getId(), prepaid, "400", null, "{\"vendorId\":11}");

        List<Map<String, Object>> rows = auxOf(PERIOD);

        assertEquals(1, rows.size());
        assertEquals(0, new BigDecimal("400").compareTo(new BigDecimal(rows.get(0).get("cumulative_debit").toString())),
                "本期即年初（202401 起），累计应等于本期 400");
    }
}
