package com.huicai.base.voucher.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.balance.service.SubjectBalanceService;
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

    private PeriodCloseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PeriodCloseServiceImpl(voucherMapper, voucherEntryMapper,
                subjectBalanceService, periodService, subjectService, subjectMapper);
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
}