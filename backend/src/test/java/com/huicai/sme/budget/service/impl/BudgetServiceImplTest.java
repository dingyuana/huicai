package com.huicai.sme.budget.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.sme.budget.constant.BudgetStatus;
import com.huicai.sme.budget.entity.BudgetEntity;
import com.huicai.sme.budget.entity.BudgetEntryEntity;
import com.huicai.sme.budget.mapper.BudgetAdjustmentMapper;
import com.huicai.sme.budget.mapper.BudgetEntryMapper;
import com.huicai.sme.budget.mapper.BudgetMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

/**
 * BudgetServiceImpl 专用测试 — 覆盖预算创建/审批/控制检查/执行分析
 *
 * <p>P16 SPEC 要求：补 5 个单测 + 文档化预算状态机
 * 验收标准：AT-P16-1 ~ AT-P16-5
 */
@ExtendWith(MockitoExtension.class)
class BudgetServiceImplTest {

    @Mock
    private BudgetMapper budgetMapper;

    @Mock
    private BudgetEntryMapper entryMapper;

    @Mock
    private BudgetAdjustmentMapper adjustmentMapper;

    private BudgetServiceImpl service;

    private static final Long BUDGET_ID = 100L;
    private static final Long SUBJECT_ID = 6601L;
    private static final String PERIOD = "202607";

    @BeforeEach
    void setUp() {
        service = new BudgetServiceImpl(budgetMapper, entryMapper, adjustmentMapper);
    }

    // ==================== AT-P16-1: 创建预算 ====================

    @Test
    @DisplayName("create — 设置状态 DRAFT，插入主表和明细，汇总总金额")
    void create_设置状态DRAFT_插入主表和明细() {
        // 准备
        BudgetEntity entity = new BudgetEntity();
        entity.setPeriod(PERIOD);
        entity.setBudgetType("EXPENSE");

        List<BudgetEntryEntity> entries = new ArrayList<>();
        BudgetEntryEntity entry1 = new BudgetEntryEntity();
        entry1.setSubjectId(SUBJECT_ID);
        entry1.setAmount(new BigDecimal("30000.00"));
        entries.add(entry1);

        BudgetEntryEntity entry2 = new BudgetEntryEntity();
        entry2.setSubjectId(6602L);
        entry2.setAmount(new BigDecimal("20000.00"));
        entries.add(entry2);

        when(budgetMapper.insert(any(BudgetEntity.class))).thenAnswer(invocation -> {
            BudgetEntity e = invocation.getArgument(0);
            e.setId(BUDGET_ID);
            return 1;
        });

        // 执行
        BudgetEntity result = service.create(entity, entries);

        // 验证
        assertNotNull(result);
        assertEquals(BUDGET_ID, result.getId());
        assertEquals(BudgetStatus.BUDGET_DRAFT, result.getStatus());
        assertEquals(0, result.getTotalAmount().compareTo(new BigDecimal("50000.00")));

        // 验证明细插入了 budgetId
        verify(budgetMapper, times(1)).insert(any(BudgetEntity.class));
        verify(entryMapper, times(2)).insert(any(BudgetEntryEntity.class));
        assertEquals(BUDGET_ID, entry1.getBudgetId());
        assertEquals(BUDGET_ID, entry2.getBudgetId());
        assertEquals("WARN", entry1.getControlType());
        assertEquals(BigDecimal.ZERO, entry1.getUsedAmount());
    }

    // ==================== AT-P16-3: 审批预算 ====================

    @Test
    @DisplayName("approve — SUBMITTED 状态变为 APPROVED")
    void approve_SUBMITTED_变APPROVED() {
        // 准备
        BudgetEntity entity = new BudgetEntity();
        entity.setId(BUDGET_ID);
        entity.setStatus(BudgetStatus.BUDGET_SUBMITTED);
        entity.setTotalAmount(new BigDecimal("50000.00"));
        when(budgetMapper.selectById(BUDGET_ID)).thenReturn(entity);
        when(budgetMapper.updateById(any(BudgetEntity.class))).thenReturn(1);

        // 执行
        BudgetEntity result = service.approve(BUDGET_ID);

        // 验证
        assertEquals(BudgetStatus.BUDGET_APPROVED, result.getStatus());
        assertNotNull(result.getApprovedAt());

        // 用 ArgumentCaptor 验证 updateById 的参数状态
        ArgumentCaptor<BudgetEntity> captor = ArgumentCaptor.forClass(BudgetEntity.class);
        verify(budgetMapper).updateById(captor.capture());
        assertEquals(BudgetStatus.BUDGET_APPROVED, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("approve — 非 SUBMITTED 状态抛异常")
    void approve_非SUBMITTED_抛异常() {
        // 准备：DRAFT 状态不能审批
        BudgetEntity entity = new BudgetEntity();
        entity.setId(BUDGET_ID);
        entity.setStatus(BudgetStatus.BUDGET_DRAFT);
        when(budgetMapper.selectById(BUDGET_ID)).thenReturn(entity);

        // 执行 & 验证
        assertThrows(BusinessException.class, () -> service.approve(BUDGET_ID));
        verify(budgetMapper, never()).updateById(any(BudgetEntity.class));
    }

    // ==================== AT-P16-4: 预算检查 ====================

    @Test
    @DisplayName("checkBudget — 在预算内，pass=true, action=WARN")
    void checkBudget_在预算内_passTrue() {
        // 准备：预算 50000，已用 10000，新申请 5000，仍在预算内
        Map<String, Object> dbEntry = new LinkedHashMap<>();
        dbEntry.put("amount", new BigDecimal("50000.00"));
        dbEntry.put("usedAmount", new BigDecimal("10000.00"));
        dbEntry.put("controlType", "WARN");
        when(entryMapper.findBySubjectAndPeriod(SUBJECT_ID, PERIOD))
                .thenReturn(List.of(dbEntry));

        // 执行
        Map<String, Object> result = service.checkBudget(SUBJECT_ID, PERIOD, new BigDecimal("5000.00"));

        // 验证
        assertTrue((Boolean) result.get("pass"));
        assertEquals("WARN", result.get("action"));
        assertEquals("WARN", result.get("controlType"));
        assertEquals(0, ((BigDecimal) result.get("remaining")).compareTo(new BigDecimal("35000.00")));
        // 使用率 = (10000+5000)/50000*100 = 30%
        assertEquals(0, ((BigDecimal) result.get("usageRatio")).compareTo(new BigDecimal("30.00")));
    }

    @Test
    @DisplayName("checkBudget — 超预算 APPROVE 模式，pass=true, action=REQUIRE_APPROVE")
    void checkBudget_超预算_action_REQUIRE_APPROVE() {
        // 准备：预算 50000，已用 48000，新申请 5000，超过预算，APPROVE 模式
        Map<String, Object> dbEntry = new LinkedHashMap<>();
        dbEntry.put("amount", new BigDecimal("50000.00"));
        dbEntry.put("usedAmount", new BigDecimal("48000.00"));
        dbEntry.put("controlType", "APPROVE");
        when(entryMapper.findBySubjectAndPeriod(SUBJECT_ID, PERIOD))
                .thenReturn(List.of(dbEntry));

        // 执行
        Map<String, Object> result = service.checkBudget(SUBJECT_ID, PERIOD, new BigDecimal("5000.00"));

        // 验证
        assertTrue((Boolean) result.get("pass"));
        assertEquals("REQUIRE_APPROVE", result.get("action"));
        assertEquals("APPROVE", result.get("controlType"));
        // 剩余 = 50000 - 53000 = -3000
        assertEquals(0, ((BigDecimal) result.get("remaining")).compareTo(new BigDecimal("-3000.00")));
    }

    @Test
    @DisplayName("checkBudget — 无预算配置，pass=true, controlType=NONE")
    void checkBudget_无预算配置_passTrue() {
        when(entryMapper.findBySubjectAndPeriod(SUBJECT_ID, PERIOD))
                .thenReturn(List.of());

        Map<String, Object> result = service.checkBudget(SUBJECT_ID, PERIOD, new BigDecimal("5000.00"));

        assertTrue((Boolean) result.get("pass"));
        assertEquals("NONE", result.get("controlType"));
    }

    // ==================== AT-P16-5: 超预算 BLOCK 模式 ====================

    @Test
    @DisplayName("checkBudget — 超预算 BLOCK 模式，pass=false, action=BLOCK")
    void checkBudget_超预算BLOCK_passFalse() {
        // 准备：预算 50000，已用 49000，新申请 2000，超过预算，BLOCK 模式
        Map<String, Object> dbEntry = new LinkedHashMap<>();
        dbEntry.put("amount", new BigDecimal("50000.00"));
        dbEntry.put("usedAmount", new BigDecimal("49000.00"));
        dbEntry.put("controlType", "BLOCK");
        when(entryMapper.findBySubjectAndPeriod(SUBJECT_ID, PERIOD))
                .thenReturn(List.of(dbEntry));

        // 执行
        Map<String, Object> result = service.checkBudget(SUBJECT_ID, PERIOD, new BigDecimal("2000.00"));

        // 验证
        assertFalse((Boolean) result.get("pass"));
        assertEquals("BLOCK", result.get("action"));
        assertEquals("BLOCK", result.get("controlType"));
        assertEquals(0, ((BigDecimal) result.get("remaining")).compareTo(new BigDecimal("-1000.00")));
    }

    // ==================== 执行分析 ====================

    @Test
    @DisplayName("executionAnalysis — 有预算和明细，返回汇总数据")
    void executionAnalysis_有预算和明细_返回汇总() {
        // 准备：一张 APPROVED 预算单，总预算 100000，已用 60000
        BudgetEntity budget = new BudgetEntity();
        budget.setId(BUDGET_ID);
        budget.setPeriod(PERIOD);
        budget.setTotalAmount(new BigDecimal("100000.00"));
        budget.setStatus(BudgetStatus.BUDGET_APPROVED);

        List<BudgetEntity> budgets = List.of(budget);
        when(budgetMapper.selectList(any())).thenReturn(budgets);

        // 该预算单有 2 个明细条目
        BudgetEntryEntity entry1 = new BudgetEntryEntity();
        entry1.setUsedAmount(new BigDecimal("40000.00"));
        BudgetEntryEntity entry2 = new BudgetEntryEntity();
        entry2.setUsedAmount(new BigDecimal("20000.00"));
        when(entryMapper.selectList(any())).thenReturn(List.of(entry1, entry2));

        // 执行
        Map<String, Object> result = service.executionAnalysis(PERIOD);

        // 验证
        assertEquals(PERIOD, result.get("period"));
        assertEquals(0, ((BigDecimal) result.get("totalBudget")).compareTo(new BigDecimal("100000.00")));
        assertEquals(0, ((BigDecimal) result.get("totalUsed")).compareTo(new BigDecimal("60000.00")));
        assertEquals(0, ((BigDecimal) result.get("remaining")).compareTo(new BigDecimal("40000.00")));
        // 执行率 = 60000/100000*100 = 60%
        assertEquals(0, ((BigDecimal) result.get("executionRatio")).compareTo(new BigDecimal("60.00")));
        assertEquals(2, result.get("entryCount"));
    }
}