package com.huicai.module.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.finance.mapper.VoucherEntryMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.finance.service.SubjectBalanceService;
import com.huicai.module.system.entity.PeriodEntity;
import com.huicai.module.system.service.PeriodService;
import com.huicai.module.system.service.SubjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
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

    private PeriodCloseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PeriodCloseServiceImpl(voucherMapper, voucherEntryMapper,
                subjectBalanceService, periodService, subjectService);
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
        when(chain.eq(any(), eq("202607"))).thenReturn(chain);
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
}