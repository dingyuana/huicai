package com.huicai.base.voucher.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.huicai.agency.tenant.entity.EnterpriseEntity;
import com.huicai.agency.tenant.mapper.EnterpriseMapper;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.balance.entity.SubjectBalanceEntity;
import com.huicai.base.balance.service.SubjectBalanceService;
import com.huicai.base.report.service.ReportService;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.system.service.PeriodService;
import com.huicai.base.system.service.SubjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PeriodCloseServiceImpl 单元测试.
 * 验证结账/反结账对大小写状态值的正确处理.
 */
@ExtendWith(MockitoExtension.class)
class PeriodCloseServiceImplTest {

    @Mock private VoucherMapper voucherMapper;
    @Mock private VoucherEntryMapper voucherEntryMapper;
    @Mock private SubjectBalanceService subjectBalanceService;
    @Mock private PeriodService periodService;
    @Mock private SubjectService subjectService;
    @Mock private SubjectMapper subjectMapper;
    @Mock private EnterpriseMapper enterpriseMapper;
    @Mock private ReportService reportService;

    private PeriodCloseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PeriodCloseServiceImpl(voucherMapper, voucherEntryMapper,
                subjectBalanceService, periodService, subjectService, subjectMapper,
                enterpriseMapper, reportService);
    }

    private PeriodEntity stubPeriod(String status) {
        PeriodEntity p = new PeriodEntity();
        p.setId(1L);
        p.setPeriodCode("202607");
        p.setYear(2026);
        p.setMonth(7);
        p.setStartDate(LocalDate.of(2026, 7, 1));
        p.setEndDate(LocalDate.of(2026, 7, 31));
        p.setStatus(status);
        return p;
    }

    /** 模拟 periodService.lambdaQuery().eq(...).one() 返回指定期间 */
    @SuppressWarnings("unchecked")
    private void stubFindPeriod(PeriodEntity p) {
        LambdaQueryChainWrapper<PeriodEntity> chain = mock(LambdaQueryChainWrapper.class);
        when(periodService.lambdaQuery()).thenReturn(chain);
        when(chain.eq(any(), anyString())).thenReturn(chain);
        when(chain.one()).thenReturn(p);
    }

    /** 模拟结账检查通过的条件: 无未记账凭证, 试算平衡, 无草稿红冲 */
    private void stubCheckPasses() {
        when(voucherMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        Map<String, Object> trial = new HashMap<>();
        trial.put("balanced", true);
        trial.put("totalDebitTotal", BigDecimal.ZERO);
        trial.put("totalCreditTotal", BigDecimal.ZERO);
        when(subjectBalanceService.checkTrialBalance("202607")).thenReturn(trial);
        stubBalanceSheetBalanced("202607");
    }

    private void stubBalanceSheetBalanced(String period) {
        Map<String, Object> sheet = new HashMap<>();
        sheet.put("balanced", true);
        sheet.put("diff", BigDecimal.ZERO);
        when(reportService.balanceSheet(period)).thenReturn(sheet);
    }

    private void stubBalanceSheetUnbalanced(String period, String diff) {
        Map<String, Object> sheet = new HashMap<>();
        sheet.put("balanced", false);
        sheet.put("diff", new BigDecimal(diff));
        when(reportService.balanceSheet(period)).thenReturn(sheet);
    }

    // ==================== checkBeforeClose ====================

    @Test
    @DisplayName("checkBeforeClose 状态 open 小写时通过")
    void checkBeforeClose_passesWithOpenStatus() {
        stubFindPeriod(stubPeriod("open"));
        stubCheckPasses();

        Map<String, Object> r = service.checkBeforeClose("202607");
        assertTrue((Boolean) r.get("passed"));
    }

    @Test
    @DisplayName("checkBeforeClose 状态 closed 小写时抛异常")
    void checkBeforeClose_throwsWhenClosed() {
        stubFindPeriod(stubPeriod("closed"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.checkBeforeClose("202607"));
        assertTrue(ex.getMessage().contains("已结账"));
    }

    @Test
    @DisplayName("checkBeforeClose 状态 locked 小写时抛异常")
    void checkBeforeClose_throwsWhenLocked() {
        stubFindPeriod(stubPeriod("locked"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.checkBeforeClose("202607"));
        assertTrue(ex.getMessage().contains("已锁定"));
    }

    // ==================== closePeriod ====================

    @Test
    @DisplayName("closePeriod 正常执行, 设置状态为 closed 小写")
    void closePeriod_worksWithOpenStatus() {
        stubFindPeriod(stubPeriod("open"));
        stubCheckPasses();

        service.closePeriod("202607", 1L);

        // 验证 periodService.updateById 被调用, 且状态为小写 closed
        verify(periodService).updateById(argThat(e ->
                "closed".equals(e.getStatus())));
    }

    // ==================== reopenPeriod ====================

    @Test
    @DisplayName("reopenPeriod 正常执行, 设置状态为 open 小写")
    void reopenPeriod_worksWithClosedStatus() {
        stubFindPeriod(stubPeriod("closed"));

        service.reopenPeriod("202607", 1L);

        // 验证 periodService.updateById 被调用, 且状态为小写 open
        verify(periodService).updateById(argThat(e ->
                "open".equals(e.getStatus())));
    }

    @Test
    @DisplayName("reopenPeriod 非 closed 状态抛异常")
    void reopenPeriod_throwsWhenNotClosed() {
        stubFindPeriod(stubPeriod("open"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reopenPeriod("202607", 1L));
        assertTrue(ex.getMessage().contains("已结账"));
    }

    // ==================== P68 结账期间顺序约束 ====================

    private PeriodEntity periodEntity(String code, String status) {
        PeriodEntity p = new PeriodEntity();
        p.setId((long) code.hashCode());
        p.setPeriodCode(code);
        p.setYear(Integer.parseInt(code.substring(0, 4)));
        p.setMonth(Integer.parseInt(code.substring(4, 6)));
        p.setStatus(status);
        return p;
    }

    /** 某期间结账检查全过: 无未记账凭证、试算平衡、无草稿红冲 */
    private void stubCheckPassesFor(String period) {
        when(voucherMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        Map<String, Object> trial = new HashMap<>();
        trial.put("balanced", true);
        trial.put("totalDebitTotal", BigDecimal.ZERO);
        trial.put("totalCreditTotal", BigDecimal.ZERO);
        when(subjectBalanceService.checkTrialBalance(period)).thenReturn(trial);
        stubBalanceSheetBalanced(period);
    }

    /** stub 当前企业 start_period（mock 静态 EnterpriseContextHolder），返回 try 资源 */
    private MockedStatic<EnterpriseContextHolder> stubEnterpriseStartPeriod(String startPeriod) {
        MockedStatic<EnterpriseContextHolder> holder = mockStatic(EnterpriseContextHolder.class);
        holder.when(EnterpriseContextHolder::get).thenReturn(1L);
        EnterpriseEntity enterprise = new EnterpriseEntity();
        enterprise.setId(1L);
        enterprise.setStartPeriod(startPeriod);
        when(enterpriseMapper.selectById(1L)).thenReturn(enterprise);
        return holder;
    }

    @Test
    @DisplayName("场景1 首个期间(start_period=202401)且检查全过可直接结账")
    void closeOrder_firstPeriod_canClose() {
        PeriodEntity p202401 = periodEntity("202401", "open");
        stubFindPeriod(p202401);
        stubCheckPassesFor("202401");
        try (MockedStatic<EnterpriseContextHolder> holder = stubEnterpriseStartPeriod("202401")) {
            service.closePeriod("202401", 1L);
        }
        // 起始期不查询上一期
        verify(periodService, never()).getByPeriodCode("202312");
        verify(periodService).updateById(argThat(e -> "closed".equals(e.getStatus())));
    }

    @Test
    @DisplayName("场景2 上期未结账就结本期: 抛错且期间保持 open、无 UPDATE")
    void closeOrder_prevNotClosed_throwsAndNoUpdate() {
        stubFindPeriod(periodEntity("202402", "open"));
        when(periodService.getByPeriodCode("202401"))
                .thenReturn(periodEntity("202401", "open"));
        try (MockedStatic<EnterpriseContextHolder> holder = stubEnterpriseStartPeriod("202401")) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.closePeriod("202402", 1L));
            assertTrue(ex.getMessage().contains("202401 尚未结账"), ex.getMessage());
        }
        verify(periodService, never()).updateById(any());
    }

    @Test
    @DisplayName("场景3 上期已结账且本期检查全过: 允许结账")
    void closeOrder_prevClosed_canClose() {
        stubFindPeriod(periodEntity("202402", "open"));
        when(periodService.getByPeriodCode("202401"))
                .thenReturn(periodEntity("202401", "closed"));
        stubCheckPassesFor("202402");
        try (MockedStatic<EnterpriseContextHolder> holder = stubEnterpriseStartPeriod("202401")) {
            service.closePeriod("202402", 1L);
        }
        verify(periodService).updateById(argThat(e -> "closed".equals(e.getStatus())));
    }

    @Test
    @DisplayName("场景4 跨年上推: 结 start_period=202401 不要求 202312 存在")
    void closeOrder_startPeriod_crossYearRollbackAllowed() {
        stubFindPeriod(periodEntity("202401", "open"));
        stubCheckPassesFor("202401");
        try (MockedStatic<EnterpriseContextHolder> holder = stubEnterpriseStartPeriod("202401")) {
            service.closePeriod("202401", 1L);
        }
        verify(periodService, never()).getByPeriodCode("202312");
        verify(periodService).updateById(argThat(e -> "closed".equals(e.getStatus())));
    }

    @Test
    @DisplayName("上期记录缺失且本期晚于 start_period: 报错先初始化期间")
    void closeOrder_prevMissingAfterStart_throws() {
        stubFindPeriod(periodEntity("202402", "open"));
        when(periodService.getByPeriodCode("202401")).thenReturn(null);
        try (MockedStatic<EnterpriseContextHolder> holder = stubEnterpriseStartPeriod("202401")) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.closePeriod("202402", 1L));
            assertTrue(ex.getMessage().contains("202401 不存在"), ex.getMessage());
            assertTrue(ex.getMessage().contains("初始化期间"), ex.getMessage());
        }
        verify(periodService, never()).updateById(any());
    }

    @Test
    @DisplayName("存量企业无 start_period 且上期缺失: 向后兼容放行结账")
    void closeOrder_legacyNoStartPeriod_prevMissing_passes() {
        stubFindPeriod(periodEntity("202402", "open"));
        when(periodService.getByPeriodCode("202401")).thenReturn(null);
        stubCheckPassesFor("202402");
        // 不 mock 静态上下文: EnterpriseContextHolder.get() 返回 null
        service.closePeriod("202402", 1L);
        verify(periodService).updateById(argThat(e -> "closed".equals(e.getStatus())));
    }

    @Test
    @DisplayName("上期为 locked(非 closed): 仍视为未结账而拦截")
    void closeOrder_prevLocked_throws() {
        stubFindPeriod(periodEntity("202402", "open"));
        when(periodService.getByPeriodCode("202401"))
                .thenReturn(periodEntity("202401", "locked"));
        try (MockedStatic<EnterpriseContextHolder> holder = stubEnterpriseStartPeriod("202401")) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.closePeriod("202402", 1L));
            assertTrue(ex.getMessage().contains("202401 尚未结账"), ex.getMessage());
        }
        verify(periodService, never()).updateById(any());
    }

    @Test
    @DisplayName("checkBeforeClose 顺序不满足时以 issue 软提示(passed=false)而不直接抛顺序异常")
    void checkBeforeClose_orderIssue_addedAsIssue() {
        stubFindPeriod(periodEntity("202402", "open"));
        when(periodService.getByPeriodCode("202401"))
                .thenReturn(periodEntity("202401", "open"));
        stubCheckPassesFor("202402");
        try (MockedStatic<EnterpriseContextHolder> holder = stubEnterpriseStartPeriod("202401")) {
            Map<String, Object> r = service.checkBeforeClose("202402");
            assertFalse((Boolean) r.get("passed"));
            @SuppressWarnings("unchecked")
            List<String> issues = (List<String>) r.get("issues");
            assertTrue(issues.stream().anyMatch(i -> i.contains("202401 尚未结账")));
        }
        verify(periodService, never()).updateById(any());
    }

    @Test
    @DisplayName("场景5 反结账降序: 下一期已结账时反结本期被拦截")
    void reopenOrder_nextClosed_throwsAndNoUpdate() {
        stubFindPeriod(periodEntity("202401", "closed"));
        when(periodService.getByPeriodCode("202402"))
                .thenReturn(periodEntity("202402", "closed"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reopenPeriod("202401", 1L));
        assertTrue(ex.getMessage().contains("202402 已结账"), ex.getMessage());
        verify(periodService, never()).updateById(any());
    }

    @Test
    @DisplayName("场景6 反结账最末已结期: 下一期未结账时反结成功")
    void reopenOrder_lastClosed_canReopen() {
        stubFindPeriod(periodEntity("202401", "closed"));
        when(periodService.getByPeriodCode("202402"))
                .thenReturn(periodEntity("202402", "open"));
        service.reopenPeriod("202401", 1L);
        verify(periodService).updateById(argThat(e -> "open".equals(e.getStatus())));
    }

    @Test
    @DisplayName("下一期记录不存在(尚未启用): 反结本期放行")
    void reopenOrder_nextMissing_canReopen() {
        stubFindPeriod(periodEntity("202412", "closed"));
        when(periodService.getByPeriodCode("202501")).thenReturn(null);
        service.reopenPeriod("202412", 1L);
        verify(periodService).updateById(argThat(e -> "open".equals(e.getStatus())));
    }

    @Test
    @DisplayName("跨年下推: 202412 的下一期推算为 202501")
    void reopenOrder_crossYearNextRollback() {
        stubFindPeriod(periodEntity("202412", "closed"));
        when(periodService.getByPeriodCode("202501"))
                .thenReturn(periodEntity("202501", "closed"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reopenPeriod("202412", 1L));
        assertTrue(ex.getMessage().contains("202501 已结账"), ex.getMessage());
        verify(periodService, never()).updateById(any());
    }

    // ==================== P69 结账联动资产负债恒等式 ====================

    @Test
    @DisplayName("场景6 资产负债表不平衡时阻止结账, 期间状态不变且无 UPDATE")
    void closePeriod_unbalancedSheet_blocks() {
        stubFindPeriod(periodEntity("202401", "open"));
        when(voucherMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        Map<String, Object> trial = new HashMap<>();
        trial.put("balanced", true);
        trial.put("totalDebitTotal", BigDecimal.ZERO);
        trial.put("totalCreditTotal", BigDecimal.ZERO);
        when(subjectBalanceService.checkTrialBalance("202401")).thenReturn(trial);
        stubBalanceSheetUnbalanced("202401", "75929.20");
        try (MockedStatic<EnterpriseContextHolder> holder = stubEnterpriseStartPeriod("202401")) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.closePeriod("202401", 1L));
            assertTrue(ex.getMessage().contains("资产负债表不平衡"), ex.getMessage());
            assertTrue(ex.getMessage().contains("75929.20"), ex.getMessage());
        }
        verify(periodService, never()).updateById(any());
    }

    @Test
    @DisplayName("checkBeforeClose 资产负债表不平衡时以 issue 提示(passed=false)")
    void checkBeforeClose_unbalancedSheet_addsIssue() {
        stubFindPeriod(periodEntity("202401", "open"));
        when(voucherMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        Map<String, Object> trial = new HashMap<>();
        trial.put("balanced", true);
        trial.put("totalDebitTotal", BigDecimal.ZERO);
        trial.put("totalCreditTotal", BigDecimal.ZERO);
        when(subjectBalanceService.checkTrialBalance("202401")).thenReturn(trial);
        stubBalanceSheetUnbalanced("202401", "100.00");
        try (MockedStatic<EnterpriseContextHolder> holder = stubEnterpriseStartPeriod("202401")) {
            Map<String, Object> r = service.checkBeforeClose("202401");
            assertFalse((Boolean) r.get("passed"));
            @SuppressWarnings("unchecked")
            List<String> issues = (List<String>) r.get("issues");
            assertTrue(issues.stream().anyMatch(i -> i.contains("资产负债表不平衡")));
        }
    }

    // ==================== generateProfitCarryOver ====================

    @Test
    @DisplayName("结转幂等: 期间已存在结转凭证时再次结转抛异常")
    void generateProfitCarryOver_throwsWhenAlreadyCarriedOver() {
        stubFindPeriod(stubPeriod("open"));

        // 该期间已存在 3 张结转凭证(CLOSE-202608 前缀)
        when(voucherMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateProfitCarryOver("202608", 1L));
        assertTrue(ex.getMessage().contains("已存在"));
        // 未生成任何凭证
        verify(voucherMapper, never()).insert(any(VoucherEntity.class));
        verify(voucherMapper, never()).updateById(any(VoucherEntity.class));
    }

    @Test
    @DisplayName("结转幂等: 首次结转(无已存在凭证)正常生成")
    void generateProfitCarryOver_firstTime_happyPath() {
        stubFindPeriod(stubPeriod("open"));

        // 无已存在结转凭证
        when(voucherMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        Subject profit = new Subject();
        profit.setId(64L); profit.setCode("4103"); profit.setName("本年利润"); profit.setDirection("credit");
        Subject expense = new Subject();
        expense.setId(85L); expense.setCode("6602"); expense.setName("管理费用"); expense.setDirection("debit");
        when(subjectMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profit);
        when(subjectService.getById(85L)).thenReturn(expense);

        VoucherEntity v = new VoucherEntity();
        v.setId(200L); v.setStatus("POSTED"); v.setPeriod("202608"); v.setDeleted(0);
        VoucherEntryEntity entry = new VoucherEntryEntity();
        entry.setVoucherId(200L); entry.setSubjectId(85L);
        entry.setDebit(new BigDecimal("1200.00")); entry.setCredit(BigDecimal.ZERO);
        when(voucherEntryMapper.selectList(null)).thenReturn(List.of(entry));
        when(voucherMapper.selectById(200L)).thenReturn(v);
        when(voucherMapper.insert(any(VoucherEntity.class))).thenAnswer(inv -> {
            ((VoucherEntity) inv.getArgument(0)).setId(300L);
            return 1;
        });
        when(voucherEntryMapper.insert(any(VoucherEntryEntity.class))).thenReturn(1);

        Long id = service.generateProfitCarryOver("202608", 1L);
        assertEquals(300L, id);
    }

    @Test
    @DisplayName("结转: 费用科目余额结转到本年利润(借利润/贷费用)")
    void generateProfitCarryOver_expenseCarriedToProfit() {
        stubFindPeriod(stubPeriod("open"));

        // 本年利润(4103, credit) 与 管理费用(6602, debit)
        Subject profit = new Subject();
        profit.setId(64L); profit.setCode("4103"); profit.setName("本年利润"); profit.setDirection("credit");
        Subject expense = new Subject();
        expense.setId(85L); expense.setCode("6602"); expense.setName("管理费用"); expense.setDirection("debit");
        when(subjectMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profit);
        when(subjectService.getById(85L)).thenReturn(expense);

        // 期间 202608 一张已记账凭证: 借 管理费用 1200 / 贷 银行存款 1200
        VoucherEntity v = new VoucherEntity();
        v.setId(200L); v.setStatus("POSTED"); v.setPeriod("202608"); v.setDeleted(0);
        VoucherEntryEntity entry = new VoucherEntryEntity();
        entry.setVoucherId(200L); entry.setSubjectId(85L);
        entry.setDebit(new BigDecimal("1200.00")); entry.setCredit(BigDecimal.ZERO);
        when(voucherEntryMapper.selectList(null)).thenReturn(List.of(entry));
        when(voucherMapper.selectById(200L)).thenReturn(v);
        when(voucherMapper.insert(any(VoucherEntity.class))).thenAnswer(inv -> {
            ((VoucherEntity) inv.getArgument(0)).setId(300L);
            return 1;
        });
        when(voucherEntryMapper.insert(any(VoucherEntryEntity.class))).thenReturn(1);

        Long id = service.generateProfitCarryOver("202608", 1L);
        assertEquals(300L, id);

        // 两条分录: 借本年利润1200 / 贷管理费用1200
        verify(voucherEntryMapper, times(2)).insert(argThat((VoucherEntryEntity e) -> {
            if (e.getSubjectId().equals(64L)) {
                return e.getDebit().compareTo(new BigDecimal("1200.00")) == 0
                        && e.getCredit().compareTo(BigDecimal.ZERO) == 0;
            }
            if (e.getSubjectId().equals(85L)) {
                return e.getCredit().compareTo(new BigDecimal("1200.00")) == 0
                        && e.getDebit().compareTo(BigDecimal.ZERO) == 0;
            }
            return false;
        }));
    }

    @Test
    @DisplayName("结转: 无损益科目余额时返回业务提示")
    void generateProfitCarryOver_noProfitData_throws() {
        stubFindPeriod(stubPeriod("open"));

        Subject profit = new Subject();
        profit.setId(64L); profit.setCode("4103"); profit.setName("本年利润"); profit.setDirection("credit");
        when(subjectMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profit);

        // 期间 202608 一张已记账凭证但只有资产负债科目(1002)
        VoucherEntity v = new VoucherEntity();
        v.setId(200L); v.setStatus("POSTED"); v.setPeriod("202608"); v.setDeleted(0);
        VoucherEntryEntity entry = new VoucherEntryEntity();
        entry.setVoucherId(200L); entry.setSubjectId(2L);
        entry.setDebit(BigDecimal.ZERO); entry.setCredit(new BigDecimal("1200.00"));
        when(voucherEntryMapper.selectList(null)).thenReturn(List.of(entry));
        when(voucherMapper.selectById(200L)).thenReturn(v);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateProfitCarryOver("202608", 1L));
        assertTrue(ex.getMessage().contains("无损益类科目余额"));
    }

    // ==================== generateProfitDistribution ====================

    private Subject stubProfit() {
        Subject s = new Subject();
        s.setId(64L); s.setCode("4103"); s.setName("本年利润"); s.setDirection("credit");
        return s;
    }

    private Subject stubSurplus() {
        Subject s = new Subject();
        s.setId(63L); s.setCode("4101"); s.setName("盈余公积"); s.setDirection("credit");
        return s;
    }

    private Subject stubDistribution() {
        Subject s = new Subject();
        s.setId(65L); s.setCode("4104"); s.setName("利润分配"); s.setDirection("credit");
        return s;
    }

    /** 模拟按科目代码查询: 顺序返回 4103→本年利润, 4101→盈余公积, 4104→利润分配 */
    @SuppressWarnings("unchecked")
    private void stubSubjects(Subject profit, Subject surplus, Subject distribution) {
        when(subjectMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(profit, surplus, distribution);
    }

    private SubjectBalanceEntity balance(Long subjectId, BigDecimal endBalance) {
        SubjectBalanceEntity b = new SubjectBalanceEntity();
        b.setSubjectId(subjectId);
        b.setPeriod("202607");
        b.setEndBalance(endBalance);
        return b;
    }

    @Test
    @DisplayName("利润分配成功: 盈利84,050 → 提取8,405 (借利润分配/贷盈余公积)")
    void generateProfitDistribution_success() {
        stubFindPeriod(stubPeriod("open"));
        // 幂等: 无已存在利润分配凭证
        when(voucherMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        Subject profit = stubProfit();
        stubSubjects(profit, stubSurplus(), stubDistribution());
        // 本年利润期末余额 84,050 (盈利)
        when(subjectBalanceService.queryByPeriod("202607"))
                .thenReturn(List.of(balance(64L, new BigDecimal("84050.00"))));

        when(voucherMapper.insert(any(VoucherEntity.class))).thenAnswer(inv -> {
            ((VoucherEntity) inv.getArgument(0)).setId(400L);
            return 1;
        });
        when(voucherEntryMapper.insert(any(VoucherEntryEntity.class))).thenReturn(1);

        Long id = service.generateProfitDistribution("202607", 1L);
        assertEquals(400L, id);

        // 两条分录: 借利润分配(65) 8405 / 贷盈余公积(63) 8405
        verify(voucherEntryMapper, times(2)).insert(argThat((VoucherEntryEntity e) -> {
            if (e.getSubjectId().equals(65L)) {
                return e.getDebit().compareTo(new BigDecimal("8405.00")) == 0
                        && e.getCredit().compareTo(BigDecimal.ZERO) == 0;
            }
            if (e.getSubjectId().equals(63L)) {
                return e.getCredit().compareTo(new BigDecimal("8405.00")) == 0
                        && e.getDebit().compareTo(BigDecimal.ZERO) == 0;
            }
            return false;
        }));
    }

    @Test
    @DisplayName("利润分配幂等: 期间已存在利润分配凭证时抛异常")
    void generateProfitDistribution_throwsWhenAlreadyDistributed() {
        stubFindPeriod(stubPeriod("open"));
        // 该期间已存在 2 张利润分配凭证(DISTRIB-202607 前缀)
        when(voucherMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateProfitDistribution("202607", 1L));
        assertTrue(ex.getMessage().contains("利润分配"));
        verify(voucherMapper, never()).insert(any(VoucherEntity.class));
        verify(voucherEntryMapper, never()).insert(any(VoucherEntryEntity.class));
    }

    @Test
    @DisplayName("利润分配: 亏损(负余额)时不分配")
    void generateProfitDistribution_throwsWhenLoss() {
        stubFindPeriod(stubPeriod("open"));
        when(voucherMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        Subject profit = stubProfit();
        stubSubjects(profit, stubSurplus(), stubDistribution());
        when(subjectBalanceService.queryByPeriod("202607"))
                .thenReturn(List.of(balance(64L, new BigDecimal("-15950.00"))));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateProfitDistribution("202607", 1L));
        assertTrue(ex.getMessage().contains("亏损"));
        verify(voucherMapper, never()).insert(any(VoucherEntity.class));
    }

    @Test
    @DisplayName("利润分配: 本年利润无余额数据时抛异常(需先过账损益结转)")
    void generateProfitDistribution_throwsWhenNoBalance() {
        stubFindPeriod(stubPeriod("open"));
        when(voucherMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        stubSubjects(stubProfit(), stubSurplus(), stubDistribution());
        // 本年利润无余额记录
        when(subjectBalanceService.queryByPeriod("202607"))
                .thenReturn(List.of(balance(85L, new BigDecimal("1000.00"))));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateProfitDistribution("202607", 1L));
        assertTrue(ex.getMessage().contains("无余额"));
        verify(voucherMapper, never()).insert(any(VoucherEntity.class));
    }
}