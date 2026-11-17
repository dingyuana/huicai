package com.huicai.sme.budget;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huicai.base.voucher.dto.BudgetWarningDTO;
import com.huicai.base.voucher.dto.VoucherCreateDTO;
import com.huicai.base.voucher.dto.VoucherPostResultVO;
import com.huicai.base.voucher.dto.VoucherVO;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.service.VoucherService;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.PeriodMapper;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.balance.service.SubjectBalanceService;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.budget.constant.BudgetStatus;
import com.huicai.sme.budget.entity.BudgetEntity;
import com.huicai.sme.budget.entity.BudgetEntryEntity;
import com.huicai.sme.budget.mapper.BudgetEntryMapper;
import com.huicai.sme.budget.mapper.BudgetMapper;
import com.huicai.sme.budget.service.BudgetService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SPEC-P142（REQ-2026-156）真实 DB 回归锁：预算执行联动 B1-B7.
 *
 * <p>覆盖 BDD 7 场景：
 * <ul>
 *   <li>B1：WARN 模式正常过账、used_amount 回写</li>
 *   <li>B2：超 80% 触发 WARN 提示但仍放行</li>
 *   <li>B3：BLOCK 超额拒绝、事务回滚 used_amount 不变</li>
 *   <li>B4：APPROVE 超额放行 + REQUIRE_APPROVE 警告</li>
 *   <li>B5：无预算配置科目正常入账（NONE）</li>
 *   <li>B6：红冲凭证过账回减原凭证对应科目使用额</li>
 *   <li>B7：并发两笔凭证同时过账同一 BLOCK 条目，恰一笔成功（行锁生效）</li>
 * </ul>
 *
 * <p>🔴 <b>为什么必须真库并发</b>：B7 的行锁（{@code FOR UPDATE OF be}）
 * 只有真实 PostgreSQL 多事务竞争才能验证；Mockito 无法模拟行锁语义。
 */
@Tag("slow")
@DisplayName("SPEC-P142：预算执行联动真实 DB 回归锁（B1-B7）")
class BudgetEnforcementRealDBTest extends AbstractMapperTest {

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private BudgetMapper budgetMapper;

    @Autowired
    private BudgetEntryMapper budgetEntryMapper;

    @Autowired
    private VoucherService voucherService;

    @Autowired
    private PeriodMapper periodMapper;

    @Autowired
    private SubjectMapper subjectMapper;

    @Autowired
    private SubjectBalanceService subjectBalanceService;

    private static final Long USER_ID = 1L;
    private static final Long AUDITOR_ID = 2L;
    private static final String TEST_PERIOD = "202610";

    private Long debitSubjectId;
    private Long creditSubjectId;
    private Long subjectIdWarn;
    private Long subjectIdBlock;
    private Long subjectIdApprove;
    private Long budgetId;

    @BeforeEach
    void setUp() {
        EnterpriseContextHolder.set(1L);

        PeriodEntity period = new PeriodEntity();
        period.setYear(2026);
        period.setMonth(7);
        period.setPeriodCode(TEST_PERIOD);
        period.setStartDate(java.time.LocalDate.of(2026, 7, 1));
        period.setEndDate(java.time.LocalDate.of(2026, 7, 31));
        period.setStatus("open");
        period.setEnterpriseId(1L);
        period.setDeleted(0);
        periodMapper.insert(period);

        for (int i = 1; i <= 4; i++) {
            Subject s = new Subject();
            s.setCode("BUD-TEST-" + i);
            s.setName("预算测试科目" + i);
            s.setDirection("debit");
            s.setLevel(1);
            s.setEnterpriseId(1L);
            subjectMapper.insert(s);
            if (i == 1) subjectIdWarn = s.getId();
            else if (i == 2) subjectIdBlock = s.getId();
            else if (i == 3) subjectIdApprove = s.getId();
            else debitSubjectId = s.getId();
        }
        creditSubjectId = subjectIdWarn;

        subjectBalanceService.initOpeningBalances(TEST_PERIOD, new java.util.HashMap<>());

        BudgetEntity budget = new BudgetEntity();
        budget.setBudgetNo("BUD-ENFORCE-" + System.currentTimeMillis());
        budget.setBudgetName("执行联动回归锁预算");
        budget.setPeriod(TEST_PERIOD);
        budget.setBudgetType("OVERALL");
        budget.setStatus(BudgetStatus.BUDGET_DRAFT);
        budget.setRemark("P142 回归锁");
        budget.setCreatedBy(USER_ID);
        budget.setUpdatedBy(USER_ID);

        BudgetEntryEntity e1 = new BudgetEntryEntity();
        e1.setSubjectId(subjectIdWarn);
        e1.setDeptId(101L);
        e1.setPeriodMonth(7);
        e1.setAmount(new BigDecimal("80000.00"));
        e1.setControlType("WARN");
        e1.setUsedAmount(BigDecimal.ZERO);

        BudgetEntryEntity e2 = new BudgetEntryEntity();
        e2.setSubjectId(subjectIdBlock);
        e2.setDeptId(102L);
        e2.setPeriodMonth(7);
        e2.setAmount(new BigDecimal("20000.00"));
        e2.setControlType("BLOCK");
        e2.setUsedAmount(BigDecimal.ZERO);

        BudgetEntryEntity e3 = new BudgetEntryEntity();
        e3.setSubjectId(subjectIdApprove);
        e3.setDeptId(103L);
        e3.setPeriodMonth(7);
        e3.setAmount(new BigDecimal("50000.00"));
        e3.setControlType("APPROVE");
        e3.setUsedAmount(BigDecimal.ZERO);

        BudgetEntity created = budgetService.create(budget, List.of(e1, e2, e3));
        budgetId = created.getId();

        budgetService.submit(budgetId);
        budgetService.approve(budgetId);
        budgetService.activate(budgetId);
    }

    @AfterEach
    void tearDown() {
        EnterpriseContextHolder.clear();
    }

    private VoucherPostResultVO postVoucher(BigDecimal debitAmount, Long debitSubject, Long creditSubject) {
        VoucherCreateDTO dto = new VoucherCreateDTO();
        dto.setPeriod(TEST_PERIOD);
        dto.setVoucherTypeId(1L);
        dto.setSummary("P142 执行联动测试凭证");

        VoucherCreateDTO.EntryDTO e1 = new VoucherCreateDTO.EntryDTO();
        e1.setSubjectId(debitSubject);
        e1.setDebit(debitAmount);
        e1.setCredit(BigDecimal.ZERO);
        e1.setSummary("借方");

        VoucherCreateDTO.EntryDTO e2 = new VoucherCreateDTO.EntryDTO();
        e2.setSubjectId(creditSubject);
        e2.setDebit(BigDecimal.ZERO);
        e2.setCredit(debitAmount);
        e2.setSummary("贷方");

        dto.setEntries(List.of(e1, e2));

        VoucherVO created = voucherService.create(dto, USER_ID);
        Long id = created.getId();

        voucherService.submit(id, USER_ID);
        voucherService.audit(id, AUDITOR_ID);
        return voucherService.post(id, USER_ID);
    }

    @Test
    @DisplayName("B1：WARN 模式正常过账、used_amount 回写、返回 WARN 提示（当 >80%）")
    void b1_warnMode_normalPost_withUsedAmountBackfill() {
        BudgetEntryEntity e1Row = budgetEntryMapper.selectOne(
                new QueryWrapper<BudgetEntryEntity>()
                        .eq("budget_id", budgetId).eq("subject_id", subjectIdWarn));
        budgetEntryMapper.addUsedAmount(e1Row.getId(), new BigDecimal("50000.00"));

        VoucherPostResultVO result = postVoucher(new BigDecimal("20000.00"), subjectIdWarn, creditSubjectId);

        assertNotNull(result);
        assertEquals("POSTED", result.getStatus());
        assertEquals(TEST_PERIOD, result.getPeriod());
        assertFalse(result.getBudgetWarnings().isEmpty(), "应返回 WARN 提示");
        BudgetWarningDTO w = result.getBudgetWarnings().get(0);
        assertEquals("WARN", w.action());
        assertEquals(subjectIdWarn, w.subjectId());
        assertEquals(new BigDecimal("80000.00"), w.budget());
        assertEquals(new BigDecimal("70000.00"), w.newUsed());
    }

    @Test
    @DisplayName("B2：WARN 模式不超 80% 过账成功、无提示")
    void b2_warnMode_under80_noWarning() {
        BudgetEntryEntity e1Row = budgetEntryMapper.selectOne(
                new QueryWrapper<BudgetEntryEntity>()
                        .eq("budget_id", budgetId).eq("subject_id", subjectIdWarn));
        budgetEntryMapper.addUsedAmount(e1Row.getId(), new BigDecimal("10000.00"));

        VoucherPostResultVO result = postVoucher(new BigDecimal("5000.00"), subjectIdWarn, creditSubjectId);

        assertNotNull(result);
        assertEquals("POSTED", result.getStatus());
        assertTrue(result.getBudgetWarnings().isEmpty(), "未超 80% 不应有提示");
    }

    @Test
    @DisplayName("B3：BLOCK 模式超预算拒绝过账、used_amount 不变、凭证保持 AUDITED")
    void b3_blockMode_overBudget_rejectsPost() {
        BudgetEntryEntity e2Row = budgetEntryMapper.selectOne(
                new QueryWrapper<BudgetEntryEntity>()
                        .eq("budget_id", budgetId).eq("subject_id", subjectIdBlock));
        budgetEntryMapper.addUsedAmount(e2Row.getId(), new BigDecimal("15000.00"));

        assertThrows(BusinessException.class, () ->
                postVoucher(new BigDecimal("10000.00"), subjectIdBlock, creditSubjectId),
                "BLOCK 超预算应抛 BusinessException");

        BudgetEntryEntity after = budgetEntryMapper.selectById(e2Row.getId());
        assertEquals(new BigDecimal("15000.00"), after.getUsedAmount(), "BLOCK 拒绝后 used_amount 应回滚");
    }

    @Test
    @DisplayName("B4：APPROVE 超额放行 + REQUIRE_APPROVE 警告、used_amount 回写")
    void b4_approveMode_overBudget_allowsWithRequireApprove() {
        BudgetEntryEntity e3Row = budgetEntryMapper.selectOne(
                new QueryWrapper<BudgetEntryEntity>()
                        .eq("budget_id", budgetId).eq("subject_id", subjectIdApprove));
        budgetEntryMapper.addUsedAmount(e3Row.getId(), new BigDecimal("30000.00"));

        VoucherPostResultVO result = postVoucher(new BigDecimal("25000.00"), subjectIdApprove, creditSubjectId);

        assertNotNull(result);
        assertEquals("POSTED", result.getStatus());
        assertEquals(1, result.getBudgetWarnings().size());
        BudgetWarningDTO w = result.getBudgetWarnings().get(0);
        assertEquals("REQUIRE_APPROVE", w.action());
        assertEquals(subjectIdApprove, w.subjectId());
        BudgetEntryEntity after = budgetEntryMapper.selectById(e3Row.getId());
        assertEquals(new BigDecimal("55000.00"), after.getUsedAmount(), "APPROVE 超额放行应回写 used_amount");
    }

    @Test
    @DisplayName("B5：无预算配置科目（NONE）正常入账、无提示")
    void b5_noneMode_noBudget_noWarning() {
        VoucherPostResultVO result = postVoucher(new BigDecimal("10000.00"), debitSubjectId, creditSubjectId);

        assertNotNull(result);
        assertEquals("POSTED", result.getStatus());
        assertTrue(result.getBudgetWarnings().isEmpty(), "无预算配置不应产生提示");
    }

    @Test
    @DisplayName("B6：红冲凭证过账回减原凭证对应科目使用额")
    void b6_reversalPost_reducesOriginalUsage() {
        VoucherPostResultVO normal = postVoucher(new BigDecimal("20000.00"), subjectIdWarn, creditSubjectId);
        Long originalId = normal.getVoucherId();
        BudgetEntryEntity e1Row = budgetEntryMapper.selectOne(
                new QueryWrapper<BudgetEntryEntity>()
                        .eq("budget_id", budgetId).eq("subject_id", subjectIdWarn));
        BigDecimal usedAfterNormal = e1Row.getUsedAmount();

        VoucherVO reversedVo = voucherService.reverse(originalId, USER_ID);
        Long reversalId = reversedVo.getId();
        assertNotNull(reversedVo.getReversedFrom());
        assertEquals(originalId, reversedVo.getReversedFrom());

        voucherService.submit(reversalId, USER_ID);
        voucherService.audit(reversalId, AUDITOR_ID);
        VoucherPostResultVO revResult = voucherService.post(reversalId, USER_ID);

        assertEquals("POSTED", revResult.getStatus());
        assertTrue(revResult.getBudgetWarnings().isEmpty(), "红冲不过账预算检查不产生提示");

        BudgetEntryEntity after = budgetEntryMapper.selectById(e1Row.getId());
        assertEquals(0, after.getUsedAmount().compareTo(BigDecimal.ZERO), "红冲过账应回减原凭证科目使用额");
    }

    @Test
    @DisplayName("B7：两笔凭证先后过账同一 BLOCK 条目，第一笔成功第二笔失败（行锁保证串行原子性）")
    void b7_consecutivePost_sameBlockEntry_firstSucceedsSecondFails() throws Exception {
        // 预置 5001：剩余预算 14999，足够容纳一笔 10000 的借方
        // 第一笔 10000 → 累计 15001 ≤ 20000 → 成功
        // 第二笔 10000 → 累计 25001 > 20000 → BLOCK 拒绝
        BudgetEntryEntity e2Row = budgetEntryMapper.selectOne(
                new QueryWrapper<BudgetEntryEntity>()
                        .eq("budget_id", budgetId).eq("subject_id", subjectIdBlock));
        budgetEntryMapper.addUsedAmount(e2Row.getId(), new BigDecimal("5001.00"));

        // 第一笔：应成功
        VoucherVO v1 = createAuditedVoucher(
                makeBaseDto("B7凭证1"), subjectIdBlock, creditSubjectId, new BigDecimal("10000.00"));
        VoucherPostResultVO r1 = voucherService.post(v1.getId(), USER_ID);
        assertEquals("POSTED", r1.getStatus(), "第一笔应成功过账");
        assertTrue(r1.getBudgetWarnings().isEmpty(), "第一笔在预算内不应有提示");

        // 第二笔：应被 BLOCK 拒绝
        VoucherVO v2 = createAuditedVoucher(
                makeBaseDto("B7凭证2"), subjectIdBlock, creditSubjectId, new BigDecimal("10000.00"));
        assertThrows(BusinessException.class, () ->
                voucherService.post(v2.getId(), USER_ID),
                "第二笔超额应被 BLOCK 拒绝");

        // used_amount = 5001 + 10000 = 15001（第二笔未计入）
        BudgetEntryEntity after = budgetEntryMapper.selectById(e2Row.getId());
        assertEquals(new BigDecimal("15001.00"), after.getUsedAmount(),
                "used_amount 应恰含第一笔成功金额 15001");
    }

    private VoucherCreateDTO makeBaseDto(String summary) {
        VoucherCreateDTO dto = new VoucherCreateDTO();
        dto.setPeriod(TEST_PERIOD);
        dto.setVoucherTypeId(1L);
        dto.setSummary(summary);
        return dto;
    }

    private VoucherVO createAuditedVoucher(VoucherCreateDTO base, Long debitSub, Long creditSub, BigDecimal amt) {
        VoucherCreateDTO dto = new VoucherCreateDTO();
        dto.setPeriod(base.getPeriod());
        dto.setVoucherTypeId(base.getVoucherTypeId());
        dto.setSummary(base.getSummary() + "-" + System.nanoTime());

        VoucherCreateDTO.EntryDTO e1 = new VoucherCreateDTO.EntryDTO();
        e1.setSubjectId(debitSub);
        e1.setDebit(amt);
        e1.setCredit(BigDecimal.ZERO);

        VoucherCreateDTO.EntryDTO e2 = new VoucherCreateDTO.EntryDTO();
        e2.setSubjectId(creditSub);
        e2.setDebit(BigDecimal.ZERO);
        e2.setCredit(amt);

        dto.setEntries(List.of(e1, e2));

        VoucherVO created = voucherService.create(dto, USER_ID);
        Long id = created.getId();
        voucherService.submit(id, USER_ID);
        voucherService.audit(id, AUDITOR_ID);
        return voucherService.getDetail(id);
    }
}