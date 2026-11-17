package com.huicai.sme.arap.e2e;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huicai.base.voucher.dto.BudgetWarningDTO;
import com.huicai.base.voucher.dto.VoucherCreateDTO;
import com.huicai.base.voucher.dto.VoucherPostResultVO;
import com.huicai.base.voucher.dto.VoucherVO;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.service.VoucherService;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.budget.constant.BudgetStatus;
import com.huicai.sme.budget.entity.BudgetEntity;
import com.huicai.sme.budget.entity.BudgetEntryEntity;
import com.huicai.sme.budget.mapper.BudgetEntryMapper;
import com.huicai.sme.budget.mapper.BudgetMapper;
import com.huicai.sme.budget.service.BudgetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 预算全流程 E2E 测试.
 * <p>
 * 模拟: 预算编制(DRAFT) → 提交(SUBMITTED) → 审批(APPROVED) → 激活(ACTIVE) → 执行检查
 * <p>
 * <b>P142 改造</b>：Step 5/6 改为走「制凭证→提交→审核→过账」真实过账链路，
 * 不再直调 {@code BudgetService.checkBudget} / {@code BudgetEntryMapper.addUsedAmount}，
 * 验证预算执行联动钩子在过账事务内正确触发。
 *
 * <h3>历史说明（REQ-2026-121）</h3>
 * 夹具有两处与 DB 约束不符：
 * <ul>
 *   <li>{@code budget_type="OPERATION"} 不在 {@code chk_budget_type}
 *       （DEPARTMENT/PROJECT/SUBJECT/OVERALL）内 → 改 OVERALL；</li>
 *   <li>{@code t_budget.budget_name} 是 NOT NULL 而原夹具未赋值（且 {@code BudgetEntity}
 *       原先根本没有 budgetName 字段）→ REQ-2026-121 已补齐 Entity 字段。</li>
 * </ul>
 */
public class BudgetFlowE2ETest extends AbstractMapperTest {

    @Autowired
    private BudgetMapper budgetMapper;

    @Autowired
    private BudgetEntryMapper budgetEntryMapper;

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private VoucherService voucherService;

    @Autowired
    private com.huicai.base.system.mapper.PeriodMapper periodMapper;

    @Autowired
    private com.huicai.base.balance.service.SubjectBalanceService subjectBalanceService;

    /** 期间与期初：凭证过账需期间 open + 期初建账，AbstractMapperTest 容器无硬编码，故在方法内建 */
    private static final String TEST_PERIOD = "202610";

    @BeforeEach
    void setUpPeriod() {
        // 企业上下文（基类已设，此处保持兼容）
        com.huicai.common.context.EnterpriseContextHolder.set(DEFAULT_ENTERPRISE_ID);

        com.huicai.base.system.entity.PeriodEntity period = new com.huicai.base.system.entity.PeriodEntity();
        period.setYear(2026);
        period.setMonth(10);
        period.setPeriodCode(TEST_PERIOD);
        period.setStartDate(java.time.LocalDate.of(2026, 10, 1));
        period.setEndDate(java.time.LocalDate.of(2026, 10, 31));
        period.setStatus("open");
        period.setEnterpriseId(DEFAULT_ENTERPRISE_ID);
        period.setDeleted(0);
        periodMapper.insert(period);

        subjectBalanceService.initOpeningBalances(TEST_PERIOD, new java.util.HashMap<>());
    }

    @Test
    void fullBudgetFlow_endToEnd_shouldCompleteSuccessfully() {
        // ==================== Step 1: 创建预算 (DRAFT) ====================
        BudgetEntity budget = new BudgetEntity();
        budget.setBudgetNo("BUD-E2E-" + System.currentTimeMillis());
        budget.setBudgetName("E2E预算全流程测试");
        budget.setPeriod("202610");
        // chk_budget_type 允许集：DEPARTMENT / PROJECT / SUBJECT / OVERALL
        budget.setBudgetType("OVERALL");
        budget.setStatus(BudgetStatus.BUDGET_DRAFT);
        budget.setRemark("E2E预算全流程测试");
        budget.setCreatedBy(1L);
        budget.setUpdatedBy(1L);

        // 预算条目1：科目 6601，部门 101，预算 80000，控制方式 WARN
        // 注意：BudgetEntryEntity.subjectId 是 t_subject 的**主键 id**，不是科目编码。
        // fk_budget_entry_subject 要求真实存在；原夹具硬编码 6601/6602 当作 id，
        // 而 6601 根本不存在（6602 的 id 也并非 6602）→ 用 ensureSubject 取得真实 id。
        Long subjectId1 = ensureSubject(DEFAULT_ENTERPRISE_ID, "6601", "销售费用", "debit");
        BudgetEntryEntity entry1 = new BudgetEntryEntity();
        entry1.setSubjectId(subjectId1);
        entry1.setDeptId(101L);
        entry1.setPeriodMonth(7);
        entry1.setAmount(new BigDecimal("80000.00"));
        entry1.setControlType("WARN");
        entry1.setUsedAmount(BigDecimal.ZERO);

        // 预算条目2：科目 6602，部门 102，预算 20000，控制方式 BLOCK
        Long subjectId2 = ensureSubject(DEFAULT_ENTERPRISE_ID, "6602", "管理费用", "debit");
        BudgetEntryEntity entry2 = new BudgetEntryEntity();
        entry2.setSubjectId(subjectId2);
        entry2.setDeptId(102L);
        entry2.setPeriodMonth(7);
        entry2.setAmount(new BigDecimal("20000.00"));
        entry2.setControlType("BLOCK");
        entry2.setUsedAmount(BigDecimal.ZERO);

        // P142：贷方科目（无预算，用于凭证借贷平衡）
        Long creditSubjectId = ensureSubject(DEFAULT_ENTERPRISE_ID, "9901", "资金账户", "credit");

        List<BudgetEntryEntity> entries = List.of(entry1, entry2);

        // 执行创建
        BudgetEntity created = budgetService.create(budget, entries);
        assertNotNull(created.getId(), "预算创建后应有 ID");
        assertEquals(BudgetStatus.BUDGET_DRAFT, created.getStatus(), "新建预算状态应为 DRAFT");
        assertEquals(0, new BigDecimal("100000.00").compareTo(created.getTotalAmount()),
                "预算总额应等于条目金额之和 (80000 + 20000 = 100000)");

        // 验证数据库持久化
        BudgetEntity savedBudget = budgetMapper.selectById(created.getId());
        assertNotNull(savedBudget, "预算应从数据库可查询");
        assertEquals(created.getBudgetNo(), savedBudget.getBudgetNo());

        // 验证条目已持久化
        QueryWrapper<BudgetEntryEntity> qw = new QueryWrapper<>();
        qw.eq("budget_id", created.getId());
        List<BudgetEntryEntity> savedEntries = budgetEntryMapper.selectList(qw);
        assertEquals(2, savedEntries.size(), "预算应包含 2 条条目");

        // ==================== Step 2: 提交预算 (DRAFT → SUBMITTED) ====================
        BudgetEntity submitted = budgetService.submit(created.getId());
        assertEquals(BudgetStatus.BUDGET_SUBMITTED, submitted.getStatus(), "提交流程后状态应为 SUBMITTED");

        // 验证数据库持久化
        BudgetEntity savedSubmitted = budgetMapper.selectById(created.getId());
        assertEquals(BudgetStatus.BUDGET_SUBMITTED, savedSubmitted.getStatus());

        // ==================== Step 3: 审批预算 (SUBMITTED → APPROVED) ====================
        BudgetEntity approved = budgetService.approve(created.getId());
        assertEquals(BudgetStatus.BUDGET_APPROVED, approved.getStatus(), "审批流程后状态应为 APPROVED");
        assertNotNull(approved.getApprovedAt(), "审批后应有审批时间");

        // 验证数据库持久化
        BudgetEntity savedApproved = budgetMapper.selectById(created.getId());
        assertEquals(BudgetStatus.BUDGET_APPROVED, savedApproved.getStatus());
        assertNotNull(savedApproved.getApprovedAt());

        // ==================== Step 4: 激活预算 (APPROVED → ACTIVE) ====================
        BudgetEntity activated = budgetService.activate(created.getId());
        assertEquals(BudgetStatus.BUDGET_ACTIVE, activated.getStatus(), "激活流程后状态应为 ACTIVE");

        // 验证数据库持久化
        BudgetEntity savedActivated = budgetMapper.selectById(created.getId());
        assertEquals(BudgetStatus.BUDGET_ACTIVE, savedActivated.getStatus());

        // ==================== Step 5: 预算执行联动 - WARN 模式（走过账链路） ====================
        // 科目 subjectId1 (6601) 预算 80000，先用 50000 (62.5%)，再过 20000 → 累计 70000 (87.5% >80%)
        // 造凭证：借方 subjectId1 20000，贷方复用 creditSubjectId
        BudgetEntryEntity e1Row = budgetEntryMapper.selectOne(
                new QueryWrapper<BudgetEntryEntity>()
                        .eq("budget_id", created.getId()).eq("subject_id", subjectId1));
        budgetEntryMapper.addUsedAmount(e1Row.getId(), new BigDecimal("50000.00"));

        VoucherPostResultVO result1 = postVoucher(new BigDecimal("20000.00"), subjectId1, creditSubjectId);

        assertNotNull(result1, "过账应返回结果");
        assertEquals("POSTED", result1.getStatus(), "WARN 模式未超额应成功过账");
        assertFalse(result1.getBudgetWarnings().isEmpty(), "累计 87.5% >80% 应返回 WARN 提示");
        BudgetWarningDTO w1 = result1.getBudgetWarnings().get(0);
        assertEquals("WARN", w1.action());
        assertEquals(subjectId1, w1.subjectId());
        assertEquals(new BigDecimal("80000.00"), w1.budget());
        assertEquals(new BigDecimal("70000.00"), w1.newUsed());

        // ==================== Step 6: 预算执行联动 - BLOCK 模式（走过账链路） ====================
        // 科目 subjectId2 (6602) 预算 20000，先模拟使用 15000，再申请 10000，累计 25000 > 20000 → BLOCK
        BudgetEntryEntity e2Row = budgetEntryMapper.selectOne(
                new QueryWrapper<BudgetEntryEntity>()
                        .eq("budget_id", created.getId()).eq("subject_id", subjectId2));
        assertNotNull(e2Row, "应能按 subject_id 精确定位到科目 6602 的预算条目");
        budgetEntryMapper.addUsedAmount(e2Row.getId(), new BigDecimal("15000.00"));

        // BLOCK 超额应抛 BusinessException，凭证不落 POSTED
        assertThrows(com.huicai.common.exception.BusinessException.class, () ->
                postVoucher(new BigDecimal("10000.00"), subjectId2, creditSubjectId),
                "BLOCK 模式超预算应抛异常拒绝过账");

        // 验证 used_amount 未变（事务回滚）
        BudgetEntryEntity afterBlock = budgetEntryMapper.selectById(e2Row.getId());
        assertEquals(new BigDecimal("15000.00"), afterBlock.getUsedAmount(),
                "BLOCK 拒绝后 used_amount 应回滚保持 15000");

        // ==================== Step 7: 预算执行分析 ====================
        // Step 5 已通过过账回写 subjectId1 used_amount = 50000(预置) + 20000(凭证) = 70000
        // Step 6 BLOCK 拒绝，subjectId2 used_amount 保持 15000
        // executionAnalysis 对 period 内所有条目 used_amount 求和 = 70000 + 15000 = 85000
        Map<String, Object> analysis = budgetService.executionAnalysis("202610");
        assertNotNull(analysis, "执行分析应返回结果");
        assertEquals("202610", analysis.get("period"), "期间应匹配");
        assertEquals(0, new BigDecimal("100000.00").compareTo((BigDecimal) analysis.get("totalBudget")),
                "总预算应为 100000");
        assertEquals(0, new BigDecimal("85000.00").compareTo((BigDecimal) analysis.get("totalUsed")),
                "总使用应为 85000 (6601:70000 + 6602:15000)");
        assertEquals(0, new BigDecimal("15000.00").compareTo((BigDecimal) analysis.get("remaining")),
                "剩余应为 15000 (100000 - 85000)");

        // ==================== 最终数据完整性验证 ====================
        BudgetEntity finalBudget = budgetMapper.selectById(created.getId());
        assertNotNull(finalBudget);
        assertEquals(BudgetStatus.BUDGET_ACTIVE, finalBudget.getStatus());
        assertTrue(finalBudget.getBudgetNo().startsWith("BUD-E2E"), "预算编号前缀应一致");

        // 验证所有条目仍存在
        List<BudgetEntryEntity> finalEntries = budgetEntryMapper.selectList(
                new QueryWrapper<BudgetEntryEntity>().eq("budget_id", created.getId()));
        assertEquals(2, finalEntries.size(), "最终应有 2 条预算条目");

        // 验证条目 used_amount 已更新
        for (BudgetEntryEntity e : finalEntries) {
            if (e.getSubjectId().equals(subjectId2)) {
                assertEquals(0, new BigDecimal("15000.00").compareTo(e.getUsedAmount()),
                        "科目6602 已使用金额应为 15000（BLOCK 阻止了追加）");
            } else if (e.getSubjectId().equals(subjectId1)) {
                // subjectId1 走了过账，used_amount = 50000 (预置) + 20000 (凭证) = 70000
                assertEquals(0, new BigDecimal("70000.00").compareTo(e.getUsedAmount()),
                        "科目6601 已使用金额应为 70000");
            } else {
                fail("出现预期外科目 " + e.getSubjectId());
            }
        }

        // ==================== REQ-2026-126 新增：维度列与控制方式真落库 ====================
        // 这 5 个字段此前全是 exist=false 幽灵字段（DB 无列），setXxx() 对 SQL 无效，
        // 读回必为 null。这里正向断言「DB 往返等于写入值」，锁死 V158 的列映射。
        BudgetEntryEntity reloadedEntry1 = budgetEntryMapper.selectById(entry1.getId());
        assertNotNull(reloadedEntry1, "条目1 应可按主键回查");
        assertEquals(101L, reloadedEntry1.getDeptId().longValue(), "部门维度应落库");
        assertEquals(7, reloadedEntry1.getPeriodMonth().intValue(), "月份维度应落库");
        assertEquals("WARN", reloadedEntry1.getControlType(), "控制方式应落库");
        assertEquals(0, new BigDecimal("70000.00").compareTo(reloadedEntry1.getUsedAmount()),
                "Step 5 过账后 entry1.used_amount 应为 70000（预置 50000 + 凭证 20000）");
        assertNotNull(reloadedEntry1.getUpdatedAt(), "updated_at 是真实列，应被 MetaObjectHandler 填充");

        BudgetEntryEntity reloadedEntry2 = budgetEntryMapper.selectById(e2Row.getId());
        assertNotNull(reloadedEntry2, "条目2 应可按主键回查");
        assertEquals(102L, reloadedEntry2.getDeptId().longValue(), "部门维度应落库");
        assertEquals(7, reloadedEntry2.getPeriodMonth().intValue(), "月份维度应落库");
        assertEquals("BLOCK", reloadedEntry2.getControlType(), "控制方式应落库");

        // 审批审计：approved_at 此前为幽灵字段，DB 往返恒 null（E2E 首个失败点）
        BudgetEntity reloadedApproved = budgetMapper.selectById(created.getId());
        assertNotNull(reloadedApproved.getApprovedAt(), "审批时间应真落库");
    }

    /**
     * P142：造凭证并走完 submit→audit→post，返回 VoucherPostResultVO（含 warnings）。
     * 用于验证预算执行联动钩子在真实过账事务内触发。
     */
    private VoucherPostResultVO postVoucher(BigDecimal debitAmount, Long debitSubject, Long creditSubject) {
        VoucherCreateDTO dto = new VoucherCreateDTO();
        dto.setPeriod(TEST_PERIOD);
        dto.setVoucherTypeId(1L);
        dto.setSummary("E2E 预算执行联动测试凭证");

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

        VoucherVO created = voucherService.create(dto, 1L);
        Long id = created.getId();

        voucherService.submit(id, 1L);
        voucherService.audit(id, 2L); // 审核人须与制单人不同（SPEC-P117）
        return voucherService.post(id, 1L);
    }
}