package com.huicai.base.balance.service.impl;

import com.huicai.base.balance.entity.SubjectBalanceEntity;
import com.huicai.base.balance.mapper.SubjectBalanceMapper;
import com.huicai.base.balance.service.SubjectBalanceService;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.system.service.SubjectService;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P98 REQ-101：期初余额跨期连续性校验（真实 DB）。
 *
 * 为什么必须走真实 DB：本校验跨两期读取 t_subject_balance 并逐科目比对，
 * 中间还涉及「回溯期序 → 批量取数 → 按科目各自回溯」三段逻辑。
 * Mockito 只能证明方法被调用，证明不了跨期数据形态与回溯命中行为——
 * 而回溯期序正是本需求的技术关键（只看紧邻上月会漏检，见 Plan F6/D1）。
 *
 * 数据构造策略：独立 enterpriseId + 两期间，避开 Flyway seed 与 dev 库既有数据。
 */
class OpeningContinuityRealDBTest extends AbstractMapperTest {

    private static final long ENT_ID = 9903L;
    private static final String PREV = "202401";
    private static final String CURR = "202407";

    @Autowired
    private SubjectBalanceService subjectBalanceService;

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
        align("t_subject_id_seq", "t_subject");
        align("t_voucher_id_seq", "t_voucher");
        align("t_voucher_entry_id_seq", "t_voucher_entry");
    }

    private void align(String seq, String table) {
        jdbcTemplate.execute("SELECT setval('" + seq + "', GREATEST((SELECT COALESCE(MAX(id), 1) FROM "
                + table + "), 1))");
    }


    @Autowired
    private SubjectService subjectService;

    @Autowired
    private SubjectBalanceMapper subjectBalanceMapper;

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

    private SubjectBalanceEntity insertBalance(Long subjectId, String period, String begin, String end) {
        SubjectBalanceEntity b = new SubjectBalanceEntity();
        b.setSubjectId(subjectId);
        b.setYear(Integer.parseInt(period.substring(0, 4)));
        b.setPeriod(period);
        b.setBeginBalance(new BigDecimal(begin));
        b.setDebitTotal(BigDecimal.ZERO);
        b.setCreditTotal(BigDecimal.ZERO);
        b.setEndBalance(new BigDecimal(end));
        // enterprise_id 由 BaseEntity 填充处理器写入，测试无登录上下文，须显式设置
        b.setEnterpriseId(ENT_ID);
        assertEquals(1, subjectBalanceMapper.insert(b));
        return b;
    }

    /** 造两期数据：银行存款两期承接一致，实收资本上期期末 300000、本期期初 200000（断层 10 万） */
    private Long givenDiscontinuity() {
        Long bank = insertSubject("1002", "银行存款", "debit");
        Long capital = insertSubject("4001", "实收资本", "credit");
        insertBalance(bank, PREV, "200000", "200000");
        insertBalance(capital, PREV, "300000", "300000");
        insertBalance(bank, CURR, "200000", "124315.70");
        insertBalance(capital, CURR, "200000", "200000");
        assertEquals(2, subjectBalanceService.queryByPeriod(PREV).size(), "上期余额行未落库");
        assertEquals(2, subjectBalanceService.queryByPeriod(CURR).size(), "本期余额行未落库");
        return capital;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mismatchesOf(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("mismatches");
    }

    private Set<Long> subjectIdsOf(String period) {
        return subjectBalanceService.queryByPeriod(period).stream()
                .map(SubjectBalanceEntity::getSubjectId)
                .collect(Collectors.toSet());
    }

    @Test
    void 期初不等于上期期末时检出() {
        givenDiscontinuity();
        assertEquals(2, subjectService.listByIds(subjectIdsOf(CURR)).size(), "科目批量读取数量不符");

        Map<String, Object> r = subjectBalanceService.checkOpeningContinuity(CURR);

        assertNotNull(r.get("mismatches"));
        assertEquals(Boolean.TRUE, r.get("checked"), "上期有余额数据时应执行比对");
        assertEquals(2, r.get("comparedCount"), "两个科目都应参与比对");
        assertEquals(Boolean.FALSE, r.get("passed"), "存在期初断层时不得判为通过");
        Map<String, Object> capitalIssue = mismatchesOf(r).stream()
                .filter(m -> "4001".equals(m.get("subjectCode")))
                .findFirst()
                .orElse(null);
        assertNotNull(capitalIssue, "必须检出 4001，实收资本断层实际未检出：" + mismatchesOf(r));
        assertEquals(0, new BigDecimal("-100000.00")
                .compareTo(new BigDecimal(capitalIssue.get("diff").toString())),
                "差额应为 本期期初200000 - 上期期末300000 = -100000");
        assertEquals(PREV, capitalIssue.get("prevPeriod"), "应回溯命中 202401");
    }

    @Test
    void 期初与上期期末一致时不误报() {
        Long bank = insertSubject("1002", "银行存款", "debit");
        insertBalance(bank, PREV, "200000", "200000");
        insertBalance(bank, CURR, "200000", "124315.70");

        Map<String, Object> r = subjectBalanceService.checkOpeningContinuity(CURR);

        assertEquals(Boolean.TRUE, r.get("passed"), "期初承接上期期末时不得误报");
        assertEquals(0, mismatchesOf(r).size());
    }

    @Test
    void 上期无余额数据时判skip而非不平() {
        Long bank = insertSubject("1002", "银行存款", "debit");
        insertBalance(bank, CURR, "50000", "60000");

        Map<String, Object> r = subjectBalanceService.checkOpeningContinuity(CURR);

        assertEquals(Boolean.FALSE, r.get("checked"), "上期无数据时不算执行了比对");
        assertNotNull(r.get("skipReason"), "必须给出 skip 原因，便于排障解释");
        assertEquals(Boolean.TRUE, r.get("passed"), "负向：新账套首期不得被判为不平，否则永远无法结账");
    }

    @Test
    void 断层科目修正期初后不再检出() {
        Long capitalId = givenDiscontinuity();
        assertFalse((Boolean) subjectBalanceService.checkOpeningContinuity(CURR).get("passed"),
                "前置：先确认断层存在");

        SubjectBalanceEntity capital = subjectBalanceService.queryByPeriod(CURR).stream()
                .filter(b -> capitalId.equals(b.getSubjectId()))
                .findFirst()
                .orElseThrow();
        capital.setBeginBalance(new BigDecimal("300000"));
        assertEquals(1, subjectBalanceMapper.updateById(capital));

        assertTrue((Boolean) subjectBalanceService.checkOpeningContinuity(CURR).get("passed"),
                "期初修正为与上期期末一致后不应再报不平");
    }

    @Test
    void 本期无余额数据时判skip() {
        Map<String, Object> r = subjectBalanceService.checkOpeningContinuity(CURR);

        assertEquals(Boolean.FALSE, r.get("checked"));
        assertNotNull(r.get("skipReason"));
        assertEquals(Boolean.TRUE, r.get("passed"), "负向：无数据不得判为不平");
    }

    @Test
    void 非法期间格式被拒() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> subjectBalanceService.checkOpeningContinuity("2024"));
        assertTrue(e.getMessage().contains("会计期间格式错误"));
    }
}
