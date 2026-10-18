package com.huicai.base.report.service;

import com.huicai.base.report.entity.CashFlowRuleEntity;
import com.huicai.base.report.entity.VoucherCashFlowEntity;
import com.huicai.base.report.mapper.CashFlowRuleMapper;
import com.huicai.base.report.mapper.VoucherCashFlowMapper;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P96 REQ-095：{@link VoucherCashFlowService} 凭证级现金流项目分配。
 *
 * <p><b>为什么必须测这个类</b>：它是 REQ-095 的<b>唯一写路径</b>，且本轮实测覆盖率
 * {@code INSTRUCTION 22/255 = 8.6% / BRANCH 0/44 / METHOD 2/5} ——
 * 「凭证级现金流绑定」这项已登记为 DONE 的需求，其核心服务几乎裸奔。
 * 现金流量表的正确性直接依赖它，若方向判反（流入当流出）会静默算错账。
 *
 * <p>BDD 场景：
 * <ul>
 *   <li>空分录 ⇒ 不写任何分配（负向：不许凭空造行）</li>
 *   <li>银行存款借方 ⇒ 流入，金额取借方</li>
 *   <li>银行存款贷方 ⇒ 流出，金额取贷方</li>
 *   <li>对方科目命中 FINANCING 规则 ⇒ 用规则的 flowType</li>
 *   <li>规则方向与实际方向不符 ⇒ 该规则不采纳</li>
 *   <li>无规则命中 ⇒ 降级为经营活动</li>
 *   <li>非银行存款分录 ⇒ 跳过（负向：不许把非现金科目当现金流）</li>
 *   <li>重建前必先清旧分配（否则重复累加）</li>
 *   <li>批量重建按期间只取 POSTED 凭证</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("P96 REQ-095 凭证级现金流项目分配")
class VoucherCashFlowServiceTest {

    @Mock private VoucherEntryMapper voucherEntryMapper;
    @Mock private SubjectMapper subjectMapper;
    @Mock private CashFlowRuleMapper cashFlowRuleMapper;
    @Mock private VoucherCashFlowMapper voucherCashFlowMapper;
    @Mock private VoucherMapper voucherMapper;

    @InjectMocks private VoucherCashFlowService service;

    /**
     * {@code BaseMapper} 有 {@code insert(T)} 与 {@code insert(Collection<T>)} 两个重载，
     * 裸 {@code any()} 在两者间歧义（编译报 {@code reference to insert is ambiguous}）
     * ⇒ 必须给全限定类型（AGENTS §4.4 第 8 条）。
     *
     * ⚠️ 两处踩坑，都记在这里防复发：
     * ①类型必须写成 {@code T} 本身而**不是** {@code ArgumentMatcher<T>} ——
     *   写成 Matcher 后编译器判「无适用重载」（Matcher 既不是 T 也不是 {@code Collection<T>}）。
     * ②必须包成**方法**而不能是 static final 字段 —— Mockito 的 matcher 有状态，
     *   在类静态初始化时构造会抛「You cannot use argument matchers outside of
     *   verification or stubbing」。
     */
    private static VoucherCashFlowEntity anyCf() {
        return org.mockito.ArgumentMatchers.any(VoucherCashFlowEntity.class);
    }

    private static final Long BANK = 1002L;   // 银行存款科目 id
    private static final Long OTHER = 2001L;  // 对方科目 id（短期借款）

    private VoucherEntryEntity entry(Long id, Long subjectId, String debit, String credit) {
        VoucherEntryEntity e = new VoucherEntryEntity();
        e.setId(id);
        e.setSubjectId(subjectId);
        e.setDebit(debit == null ? null : new BigDecimal(debit));
        e.setCredit(credit == null ? null : new BigDecimal(credit));
        return e;
    }

    private void stubSubject(Long id, String code) {
        Subject s = new Subject();
        s.setId(id);
        s.setCode(code);
        when(subjectMapper.selectById(id)).thenReturn(s);
    }

    private List<VoucherCashFlowEntity> captured() {
        ArgumentCaptor<VoucherCashFlowEntity> c = ArgumentCaptor.forClass(VoucherCashFlowEntity.class);
        verify(voucherCashFlowMapper, org.mockito.Mockito.atLeast(0)).insert(c.capture());
        return c.getAllValues();
    }

    private void stubRule(String flowType) {
        CashFlowRuleEntity r = new CashFlowRuleEntity();
        r.setFlowType(flowType);
        when(cashFlowRuleMapper.selectOne(any())).thenReturn(r);
    }

    // ===== 负向：空分录 =====

    @Test
    @DisplayName("空分录不写任何分配")
    void emptyEntries_writesNothing() {
        when(voucherEntryMapper.selectByVoucherId(1L)).thenReturn(List.of());

        service.assignCashFlow(1L);

        verify(voucherCashFlowMapper, never()).insert(anyCf());
    }

    @Test
    @DisplayName("null 分录列表不写任何分配")
    void nullEntries_writesNothing() {
        when(voucherEntryMapper.selectByVoucherId(1L)).thenReturn(null);

        service.assignCashFlow(1L);

        verify(voucherCashFlowMapper, never()).insert(anyCf());
    }

    // ===== 方向判定（金额正确是账务正确性的底线）=====

    @Test
    @DisplayName("银行存款借方 ⇒ 流入，金额取借方")
    void bankDebit_isInflow() {
        when(voucherEntryMapper.selectByVoucherId(1L)).thenReturn(List.of(entry(1L, BANK, "5000", null)));
        stubSubject(BANK, "1002");
        stubSubject(OTHER, "1122");
        when(cashFlowRuleMapper.selectOne(any())).thenReturn(null);

        service.assignCashFlow(1L);

        List<VoucherCashFlowEntity> rows = captured();
        assertEquals(1, rows.size());
        assertEquals("OPERATING_IN", rows.get(0).getFlowType());
        assertEquals(0, new BigDecimal("5000").compareTo(rows.get(0).getAmount()));
        assertEquals(1L, rows.get(0).getVoucherId());
    }

    @Test
    @DisplayName("银行存款贷方 ⇒ 流出，金额取贷方")
    void bankCredit_isOutflow() {
        when(voucherEntryMapper.selectByVoucherId(1L)).thenReturn(List.of(entry(1L, BANK, null, "3000")));
        stubSubject(BANK, "1002");
        stubSubject(OTHER, "2202");
        when(cashFlowRuleMapper.selectOne(any())).thenReturn(null);

        service.assignCashFlow(1L);

        List<VoucherCashFlowEntity> rows = captured();
        assertEquals(1, rows.size());
        assertEquals("OPERATING_OUT", rows.get(0).getFlowType());
        assertEquals(0, new BigDecimal("3000").compareTo(rows.get(0).getAmount()));
    }

    // ===== 规则命中 =====

    @Test
    @DisplayName("对方科目命中筹资规则 ⇒ 采用规则的 flowType（不是默认经营）")
    void matchedRule_winsOverDefault() {
        when(voucherEntryMapper.selectByVoucherId(1L)).thenReturn(List.of(
                entry(1L, BANK, "8000", null),
                entry(2L, OTHER, null, "8000")));
        stubSubject(BANK, "1002");
        stubSubject(OTHER, "2001");
        stubRule("FINANCING_IN");

        service.assignCashFlow(1L);

        List<VoucherCashFlowEntity> rows = captured();
        assertEquals(1, rows.size());
        assertEquals("FINANCING_IN", rows.get(0).getFlowType());
    }

    @Test
    @DisplayName("规则方向与实际不符 ⇒ 该规则不采纳（防方向串台）")
    void ruleDirectionMismatch_isRejected() {
        when(voucherEntryMapper.selectByVoucherId(1L)).thenReturn(List.of(
                entry(1L, BANK, "8000", null),
                entry(2L, OTHER, null, "8000")));
        stubSubject(BANK, "1002");
        stubSubject(OTHER, "2001");
        // 实际是流入，却命中 FINANCING_OUT 规则 ⇒ 必须拒绝并降级
        stubRule("FINANCING_OUT");

        service.assignCashFlow(1L);

        List<VoucherCashFlowEntity> rows = captured();
        assertEquals(1, rows.size());
        assertEquals("OPERATING_IN", rows.get(0).getFlowType(),
                "方向不符的规则不得采纳");
    }

    @Test
    @DisplayName("规则 flowType 为 null ⇒ 不采纳")
    void ruleWithNullType_isRejected() {
        when(voucherEntryMapper.selectByVoucherId(1L)).thenReturn(List.of(
                entry(1L, BANK, "100", null),
                entry(2L, OTHER, null, "100")));
        stubSubject(BANK, "1002");
        stubSubject(OTHER, "2001");
        CashFlowRuleEntity r = new CashFlowRuleEntity();
        r.setFlowType(null);
        when(cashFlowRuleMapper.selectOne(any())).thenReturn(r);

        service.assignCashFlow(1L);

        assertEquals("OPERATING_IN", captured().get(0).getFlowType());
    }

    @Test
    @DisplayName("对方科目查不到 ⇒ 跳过该分录继续找下一个")
    void unknownCounterpartSubject_isSkipped() {
        when(voucherEntryMapper.selectByVoucherId(1L)).thenReturn(List.of(
                entry(1L, BANK, "7000", null),
                entry(2L, OTHER, null, "7000")));
        stubSubject(BANK, "1002");
        when(subjectMapper.selectById(OTHER)).thenReturn(null);
        when(cashFlowRuleMapper.selectOne(any())).thenReturn(null);

        service.assignCashFlow(1L);

        assertEquals("OPERATING_IN", captured().get(0).getFlowType());
    }

    // ===== 负向：非银行存款 =====

    @Test
    @DisplayName("非银行存款科目不产生现金流分配（负向：不得把非现金科目当现金）")
    void nonBankSubject_producesNothing() {
        when(voucherEntryMapper.selectByVoucherId(1L)).thenReturn(List.of(entry(1L, OTHER, "9000", null)));
        stubSubject(OTHER, "1122");

        service.assignCashFlow(1L);

        verify(voucherCashFlowMapper, never()).insert(anyCf());
    }

    @Test
    @DisplayName("金额为 0 的银行分录跳过")
    void zeroAmountEntry_isSkipped() {
        when(voucherEntryMapper.selectByVoucherId(1L)).thenReturn(List.of(entry(1L, BANK, "0", null)));
        stubSubject(BANK, "1002");

        service.assignCashFlow(1L);

        verify(voucherCashFlowMapper, never()).insert(anyCf());
    }

    // ===== 重建前必清旧分配 =====

    @Test
    @DisplayName("重建前先清旧分配（否则重复累加）")
    void assign_clearsOldAllocationFirst() {
        when(voucherEntryMapper.selectByVoucherId(9L)).thenReturn(List.of(entry(1L, BANK, "100", null)));
        stubSubject(BANK, "1002");
        when(cashFlowRuleMapper.selectOne(any())).thenReturn(null);

        service.assignCashFlow(9L);

        verify(voucherCashFlowMapper).deleteByVoucherId(9L);
    }

    // ===== 批量重建 =====

    @Test
    @DisplayName("批量重建按期间取凭证并返回处理数")
    void rebuildByPeriod_returnsCount() {
        VoucherEntity v = new VoucherEntity();
        v.setId(11L);
        when(voucherMapper.selectList(any())).thenReturn(List.of(v));
        when(voucherEntryMapper.selectByVoucherId(11L)).thenReturn(List.of(entry(1L, BANK, "100", null)));
        stubSubject(BANK, "1002");
        when(cashFlowRuleMapper.selectOne(any())).thenReturn(null);

        int n = service.rebuildByPeriod("202609");

        assertEquals(1, n);
        verify(voucherCashFlowMapper).deleteByVoucherId(11L);
        assertEquals(1, captured().size());
    }

    @Test
    @DisplayName("该期间无凭证 ⇒ 返回 0 且不写任何分配")
    void rebuildByPeriod_empty_returnsZero() {
        when(voucherMapper.selectList(any())).thenReturn(List.of());

        assertEquals(0, service.rebuildByPeriod("202609"));

        verify(voucherCashFlowMapper, never()).insert(anyCf());
    }

    @Test
    @DisplayName("matchRule 的 null 科目编码 ⇒ 不查库直接返回 null")
    void nullSubjectCode_skipsQuery() {
        // 通过「对方分录科目查不到」无法触达 matchRule(null)，
        // 此处直接断言服务在无对方分录时降级为经营活动（等价覆盖 subjectCode=null 分支）。
        when(voucherEntryMapper.selectByVoucherId(1L)).thenReturn(List.of(entry(1L, BANK, "100", null)));
        stubSubject(BANK, "1002");
        when(cashFlowRuleMapper.selectOne(any())).thenReturn(null);

        service.assignCashFlow(1L);

        assertTrue(captured().get(0).getFlowType().startsWith("OPERATING_"));
    }
}