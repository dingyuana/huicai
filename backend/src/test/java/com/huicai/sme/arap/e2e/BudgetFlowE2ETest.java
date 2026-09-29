package com.huicai.sme.arap.e2e;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.budget.constant.BudgetStatus;
import com.huicai.sme.budget.entity.BudgetEntity;
import com.huicai.sme.budget.entity.BudgetEntryEntity;
import com.huicai.sme.budget.mapper.BudgetEntryMapper;
import com.huicai.sme.budget.mapper.BudgetMapper;
import com.huicai.sme.budget.service.BudgetService;
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
 * 一个 @Test 方法完成完整流程，使用 @Transactional 自动回滚清理数据.
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

    @Test
    void fullBudgetFlow_endToEnd_shouldCompleteSuccessfully() {
        // ==================== Step 1: 创建预算 (DRAFT) ====================
        BudgetEntity budget = new BudgetEntity();
        budget.setBudgetNo("BUD-E2E-" + System.currentTimeMillis());
        budget.setBudgetName("E2E预算全流程测试");
        budget.setPeriod("202607");
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

        // ==================== Step 5: 预算检查 - 未超预算 (WARN 模式) ====================
        // 科目 6601 申请 30000，在预算 80000 范围内，通过
        // ⚠️ REQ-2026-126：原代码传 6601L（科目**编码**），而
        // findBySubjectAndPeriod 按 be.subject_id（**主键 id**）匹配，
        // ensureSubject 返回的是自增 id 而非编码 → 必然查不到预算。
        Map<String, Object> checkResult = budgetService.checkBudget(subjectId1, "202607", new BigDecimal("30000.00"));
        assertNotNull(checkResult, "预算检查应返回结果");
        assertTrue((Boolean) checkResult.get("pass"), "未超预算时应通过检查");
        assertEquals("WARN", checkResult.get("controlType"), "控制方式应为 WARN");
        assertEquals(0, new BigDecimal("80000.00").compareTo((BigDecimal) checkResult.get("budget")),
                "预算金额应为 80000");
        assertEquals(0, new BigDecimal("30000.00").compareTo((BigDecimal) checkResult.get("newUsed")),
                "新使用金额应为 30000");

        // ==================== Step 6: 预算检查 - 超预算 (BLOCK 模式) ====================
        // 科目 6602 预算 20000，先模拟使用 15000，再申请 10000，累计 25000 > 20000，应被阻止
        // ⚠️ REQ-2026-126：原代码用 savedEntries.get(1) 定位条目2，而 selectList
        // 无 ORDER BY，返回顺序无保证 → 改为按 subject_id 精确定位。
        BudgetEntryEntity entry2Row = budgetEntryMapper.selectOne(
                new QueryWrapper<BudgetEntryEntity>()
                        .eq("budget_id", created.getId())
                        .eq("subject_id", subjectId2));
        assertNotNull(entry2Row, "应能按 subject_id 精确定位到科目 6602 的预算条目");
        budgetEntryMapper.addUsedAmount(entry2Row.getId(), new BigDecimal("15000.00"));

        Map<String, Object> overResult = budgetService.checkBudget(subjectId2, "202607", new BigDecimal("10000.00"));
        assertNotNull(overResult, "超预算检查应返回结果");
        assertFalse((Boolean) overResult.get("pass"), "BLOCK 模式超预算时应不通过");
        assertEquals("BLOCK", overResult.get("action"), "超预算时 action 应为 BLOCK");
        assertEquals(0, new BigDecimal("20000.00").compareTo((BigDecimal) overResult.get("budget")),
                "预算金额应为 20000");
        assertEquals(0, new BigDecimal("25000.00").compareTo((BigDecimal) overResult.get("newUsed")),
                "新使用金额应为 25000 (15000 + 10000)");

        // ==================== Step 7: 预算执行分析 ====================
        Map<String, Object> analysis = budgetService.executionAnalysis("202607");
        assertNotNull(analysis, "执行分析应返回结果");
        assertEquals("202607", analysis.get("period"), "期间应匹配");
        assertEquals(0, new BigDecimal("100000.00").compareTo((BigDecimal) analysis.get("totalBudget")),
                "总预算应为 100000");
        assertEquals(0, new BigDecimal("15000.00").compareTo((BigDecimal) analysis.get("totalUsed")),
                "总使用应为 15000 (仅科目6602使用了15000)");
        assertEquals(0, new BigDecimal("85000.00").compareTo((BigDecimal) analysis.get("remaining")),
                "剩余应为 85000 (100000 - 15000)");

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
                        "科目6602 已使用金额应为 15000");
            } else if (e.getSubjectId().equals(subjectId1)) {
                assertEquals(0, BigDecimal.ZERO.compareTo(e.getUsedAmount()),
                        "科目6601 已使用金额应为 0");
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
        assertEquals(0, BigDecimal.ZERO.compareTo(reloadedEntry1.getUsedAmount()), "已用额应落库且为 0");
        assertNotNull(reloadedEntry1.getUpdatedAt(), "updated_at 是真实列，应被 MetaObjectHandler 填充");

        BudgetEntryEntity reloadedEntry2 = budgetEntryMapper.selectById(entry2Row.getId());
        assertNotNull(reloadedEntry2, "条目2 应可按主键回查");
        assertEquals(102L, reloadedEntry2.getDeptId().longValue(), "部门维度应落库");
        assertEquals(7, reloadedEntry2.getPeriodMonth().intValue(), "月份维度应落库");
        assertEquals("BLOCK", reloadedEntry2.getControlType(), "控制方式应落库");

        // 审批审计：approved_at 此前为幽灵字段，DB 往返恒 null（E2E 首个失败点）
        BudgetEntity reloadedApproved = budgetMapper.selectById(created.getId());
        assertNotNull(reloadedApproved.getApprovedAt(), "审批时间应真落库");
    }
}