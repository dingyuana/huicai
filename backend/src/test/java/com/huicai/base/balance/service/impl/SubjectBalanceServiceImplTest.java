package com.huicai.base.balance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.huicai.agency.tenant.entity.EnterpriseEntity;
import com.huicai.agency.tenant.mapper.EnterpriseMapper;
import com.huicai.base.balance.entity.SubjectBalanceEntity;
import com.huicai.base.balance.mapper.SubjectBalanceMapper;
import com.huicai.base.balance.service.SubjectBalanceService;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.service.PeriodService;
import com.huicai.base.system.service.SubjectService;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SubjectBalanceServiceImpl 单元测试 — 覆盖期初建账/锁定/解锁/清空/前置强制等关键业务规则
 */
@ExtendWith(MockitoExtension.class)
class SubjectBalanceServiceImplTest {

    @Mock private SubjectBalanceMapper subjectBalanceMapper;
    @Mock private SubjectService subjectService;
    @Mock private PeriodService periodService;
    @Mock private VoucherMapper voucherMapper;
    @Mock private EnterpriseMapper enterpriseMapper;

    @InjectMocks
    private SubjectBalanceServiceImpl service;

    private static final String PERIOD = "202608";

    private PeriodEntity newPeriod(String status, String openingStatus) {
        PeriodEntity p = new PeriodEntity();
        p.setId(1L);
        p.setYear(2026);
        p.setMonth(8);
        p.setPeriodCode(PERIOD);
        p.setStatus(status);
        p.setOpeningStatus(openingStatus);
        return p;
    }

    private Subject newDebitSubject(Long id, boolean isLeaf) {
        Subject s = new Subject();
        s.setId(id);
        s.setCode("1001");
        s.setName("库存现金");
        s.setDirection("debit");
        s.setIsLeaf(isLeaf);
        return s;
    }

    private Subject newCreditSubject(Long id) {
        Subject s = new Subject();
        s.setId(id);
        s.setCode("4001");
        s.setName("实收资本");
        s.setDirection("credit");
        s.setIsLeaf(true);
        return s;
    }

    // ============================================================
    // initOpeningBalances
    // ============================================================

    @Test
    @DisplayName("期初建账_期间不存在_throw badRequest")
    void init_periodNotExist_throws() {
        when(periodService.getByPeriodCode(PERIOD)).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> service.initOpeningBalances(PERIOD, new HashMap<>()));
    }

    @Test
    @DisplayName("期初建账_期间已closed_throw badRequest")
    void init_periodClosed_throws() {
        when(periodService.getByPeriodCode(PERIOD)).thenReturn(newPeriod("closed", "none"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.initOpeningBalances(PERIOD, new HashMap<>()));
        assertTrue(ex.getMessage().contains("closed"));
    }

    @Test
    @DisplayName("期初建账_期初已锁定_throw conflict")
    void init_alreadyLocked_throws() {
        when(periodService.getByPeriodCode(PERIOD)).thenReturn(newPeriod("open", "locked"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.initOpeningBalances(PERIOD, new HashMap<>()));
        assertTrue(ex.getMessage().contains("锁定"));
    }

    @Test
    @DisplayName("期初建账_已有余额数据_throw conflict")
    void init_existingData_throws() {
        when(periodService.getByPeriodCode(PERIOD)).thenReturn(newPeriod("open", "none"));
        when(subjectBalanceMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        assertThrows(BusinessException.class,
                () -> service.initOpeningBalances(PERIOD, Map.of(1L, BigDecimal.TEN)));
    }

    @Test
    @DisplayName("期初建账_空balances_标记entered不插入")
    void init_emptyBalances_marksEntered() {
        when(periodService.getByPeriodCode(PERIOD)).thenReturn(newPeriod("open", "none"));
        when(subjectBalanceMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        service.initOpeningBalances(PERIOD, new HashMap<>());

        verify(subjectBalanceMapper, never()).insert(any(SubjectBalanceEntity.class));
        verify(periodService).markOpeningEntered(eq(PERIOD), any(), any(), any());
    }

    @Test
    @DisplayName("期初建账_借贷不平衡_throw badRequest")
    void init_unbalanced_throws() {
        when(periodService.getByPeriodCode(PERIOD)).thenReturn(newPeriod("open", "none"));
        when(subjectBalanceMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(subjectService.getById(1L)).thenReturn(newDebitSubject(1L, true));
        when(subjectService.getById(2L)).thenReturn(newCreditSubject(2L));

        Map<Long, BigDecimal> balances = new HashMap<>();
        balances.put(1L, new BigDecimal("1000"));
        balances.put(2L, new BigDecimal("500"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.initOpeningBalances(PERIOD, balances));
        assertTrue(ex.getMessage().contains("试算不平衡"));
    }

    @Test
    @DisplayName("期初建账_非末级科目_throw badRequest")
    void init_nonLeafSubject_throws() {
        when(periodService.getByPeriodCode(PERIOD)).thenReturn(newPeriod("open", "none"));
        when(subjectBalanceMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(subjectService.getById(1L)).thenReturn(newDebitSubject(1L, false));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.initOpeningBalances(PERIOD, Map.of(1L, BigDecimal.TEN)));
        assertTrue(ex.getMessage().contains("非末级科目"));
    }

    @Test
    @DisplayName("期初建账_借贷平衡_success")
    void init_balanced_success() {
        when(periodService.getByPeriodCode(PERIOD)).thenReturn(newPeriod("open", "none"));
        when(subjectBalanceMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(subjectService.getById(1L)).thenReturn(newDebitSubject(1L, true));
        when(subjectService.getById(2L)).thenReturn(newCreditSubject(2L));

        Map<Long, BigDecimal> balances = new HashMap<>();
        balances.put(1L, new BigDecimal("400000"));
        balances.put(2L, new BigDecimal("400000"));

        service.initOpeningBalances(PERIOD, balances);

        verify(subjectBalanceMapper, times(2)).insert(any(SubjectBalanceEntity.class));
        verify(periodService).markOpeningEntered(eq(PERIOD), any(), any(), any());
    }

    @Test
    @DisplayName("期初建账_指定建账日期_透传markOpeningEntered(P58)")
    void init_withOpenedAt_passesThrough() {
        when(periodService.getByPeriodCode(PERIOD)).thenReturn(newPeriod("open", "none"));
        when(subjectBalanceMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(subjectService.getById(1L)).thenReturn(newDebitSubject(1L, true));
        when(subjectService.getById(2L)).thenReturn(newCreditSubject(2L));

        java.time.LocalDateTime openedAt = java.time.LocalDateTime.of(2026, 8, 1, 10, 30);
        Map<Long, BigDecimal> balances = new HashMap<>();
        balances.put(1L, new BigDecimal("400000"));
        balances.put(2L, new BigDecimal("400000"));

        service.initOpeningBalances(PERIOD, openedAt, balances);

        verify(periodService).markOpeningEntered(eq(PERIOD), eq(openedAt), any(), any());
    }

    @Test
    @DisplayName("期初建账_不传建账日期_默认当前时间(P58)")
    void init_withoutOpenedAt_defaultsNow() {
        when(periodService.getByPeriodCode(PERIOD)).thenReturn(newPeriod("open", "none"));
        when(subjectBalanceMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(subjectService.getById(1L)).thenReturn(newDebitSubject(1L, true));
        when(subjectService.getById(2L)).thenReturn(newCreditSubject(2L));

        Map<Long, BigDecimal> balances = new HashMap<>();
        balances.put(1L, new BigDecimal("400000"));
        balances.put(2L, new BigDecimal("400000"));

        service.initOpeningBalances(PERIOD, balances);

        verify(periodService).markOpeningEntered(eq(PERIOD), argThat(t -> t != null), any(), any());
    }

    // ============================================================
    // lockOpeningBalances
    // ============================================================

    @Test
    @DisplayName("锁定期初_尚未建账_throw badRequest")
    void lock_notEntered_throws() {
        when(periodService.getByPeriodCode(PERIOD)).thenReturn(newPeriod("open", "none"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.lockOpeningBalances(PERIOD));
        assertTrue(ex.getMessage().contains("尚未完成期初建账"));
    }

    @Test
    @DisplayName("锁定期初_已成功（试算平衡通过）")
    void lock_balanced_success() {
        when(periodService.getByPeriodCode(PERIOD)).thenReturn(newPeriod("open", "entered"));
        // 查询该期间所有余额用于试算
        SubjectBalanceEntity b1 = new SubjectBalanceEntity();
        b1.setSubjectId(1L);
        b1.setBeginBalance(new BigDecimal("400000"));
        b1.setDebitTotal(BigDecimal.ZERO);
        b1.setCreditTotal(BigDecimal.ZERO);
        b1.setEndBalance(new BigDecimal("400000"));
        SubjectBalanceEntity b2 = new SubjectBalanceEntity();
        b2.setSubjectId(2L);
        b2.setBeginBalance(new BigDecimal("400000"));
        b2.setDebitTotal(BigDecimal.ZERO);
        b2.setCreditTotal(BigDecimal.ZERO);
        b2.setEndBalance(new BigDecimal("400000"));
        when(subjectBalanceMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(java.util.List.of(b1, b2));
        when(subjectService.getById(1L)).thenReturn(newDebitSubject(1L, true));
        when(subjectService.getById(2L)).thenReturn(newCreditSubject(2L));

        service.lockOpeningBalances(PERIOD);

        verify(periodService).setOpeningStatus(PERIOD, "locked");
    }

    // ============================================================
    // unlockOpeningBalances
    // ============================================================

    @Test
    @DisplayName("解锁期初_未处于锁定状态_throw")
    void unlock_notLocked_throws() {
        when(periodService.getByPeriodCode(PERIOD)).thenReturn(newPeriod("open", "entered"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.unlockOpeningBalances(PERIOD));
        assertTrue(ex.getMessage().contains("未处于锁定状态"));
    }

    @Test
    @DisplayName("解锁期初_已锁定期间_一律拒绝(终态不可回退)")
    void unlock_lockedPeriod_throws() {
        when(periodService.getByPeriodCode(PERIOD)).thenReturn(newPeriod("open", "locked"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.unlockOpeningBalances(PERIOD));
        assertTrue(ex.getMessage().contains("期初已锁定"), () -> "实际: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("红冲"), () -> "应提示红冲修正, 实际: " + ex.getMessage());
        verify(periodService, never()).setOpeningStatus(anyString(), anyString());
    }

    // ============================================================
    // clearOpeningBalances
    // ============================================================

    @Test
    @DisplayName("清空期初_期初已锁定_throw conflict")
    void clear_locked_throws() {
        when(periodService.getByPeriodCode(PERIOD)).thenReturn(newPeriod("open", "locked"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.clearOpeningBalances(PERIOD));
        assertTrue(ex.getMessage().contains("已锁定"));
        verify(subjectBalanceMapper, never()).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("清空期初_有已过账凭证_throw conflict")
    void clear_withPostedVoucher_throws() {
        when(periodService.getByPeriodCode(PERIOD)).thenReturn(newPeriod("open", "entered"));
        when(voucherMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.clearOpeningBalances(PERIOD));
        assertTrue(ex.getMessage().contains("已过账凭证"));
        verify(subjectBalanceMapper, never()).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("清空期初_无凭证_success_物理删除行并置none")
    void clear_clean_success() {
        when(periodService.getByPeriodCode(PERIOD)).thenReturn(newPeriod("open", "entered"));
        when(voucherMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(subjectBalanceMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(3);

        service.clearOpeningBalances(PERIOD);

        verify(subjectBalanceMapper).delete(any(LambdaQueryWrapper.class));
        verify(periodService).setOpeningStatus(PERIOD, "none");
    }

    // ============================================================
    // validateOpeningBeforePost
    // ============================================================

    @Test
    @DisplayName("过账前置_期间不存在_放行")
    void validate_periodNotExist_pass() {
        when(periodService.getByPeriodCode("209912")).thenReturn(null);
        // 不抛
        service.validateOpeningBeforePost("209912");
    }

    @Test
    @DisplayName("过账前置_最早期且未建账_throw badRequest")
    void validate_earliestUnentered_throws() {
        PeriodEntity target = newPeriod("open", "none");
        target.setPeriodCode(PERIOD);
        when(periodService.getByPeriodCode(PERIOD)).thenReturn(target);
        when(periodService.getOne(any(LambdaQueryWrapper.class))).thenReturn(target);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateOpeningBeforePost(PERIOD));
        assertTrue(ex.getMessage().contains("尚未完成期初建账"));
    }

    @Test
    @DisplayName("过账前置_非最早期_即使未建账也放行")
    void validate_notEarliest_pass() {
        PeriodEntity target = newPeriod("open", "none");
        target.setPeriodCode("202608");
        when(periodService.getByPeriodCode("202608")).thenReturn(target);

        PeriodEntity earliest = newPeriod("open", "entered");
        earliest.setPeriodCode("202607");
        when(periodService.getOne(any(LambdaQueryWrapper.class))).thenReturn(earliest);

        // 不抛
        service.validateOpeningBeforePost("202608");
    }

    @Test
    @DisplayName("过账前置_已entered_放行")
    void validate_entered_pass() {
        when(periodService.getByPeriodCode(PERIOD)).thenReturn(newPeriod("open", "entered"));
        // 不抛
        service.validateOpeningBeforePost(PERIOD);
    }

    // ============================================================
    // start_period 建账期间（P57）
    // ============================================================

    private void withEnterpriseContext(Long enterpriseId) {
        EnterpriseContextHolder.set(enterpriseId);
    }

    @Test
    @DisplayName("期初建账成功_回填企业start_period")
    void init_success_backfillsStartPeriod() {
        withEnterpriseContext(1L);
        try {
            when(periodService.getByPeriodCode(PERIOD)).thenReturn(newPeriod("open", "none"));
            when(subjectBalanceMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(subjectService.getById(1L)).thenReturn(newDebitSubject(1L, true));
            when(subjectService.getById(2L)).thenReturn(newCreditSubject(2L));

            EnterpriseEntity enterprise = new EnterpriseEntity();
            enterprise.setId(1L);
            when(enterpriseMapper.selectById(1L)).thenReturn(enterprise);

            Map<Long, BigDecimal> balances = new HashMap<>();
            balances.put(1L, new BigDecimal("400000"));
            balances.put(2L, new BigDecimal("400000"));

            service.initOpeningBalances(PERIOD, balances);

            verify(enterpriseMapper).updateById(argThat((EnterpriseEntity e) ->
                    e.getId().equals(1L) && PERIOD.equals(e.getStartPeriod())));
        } finally {
            EnterpriseContextHolder.clear();
        }
    }

    @Test
    @DisplayName("期初建账成功_已有start_period_不覆盖")
    void init_success_startPeriodExists_noOverwrite() {
        withEnterpriseContext(1L);
        try {
            when(periodService.getByPeriodCode(PERIOD)).thenReturn(newPeriod("open", "none"));
            when(subjectBalanceMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(subjectService.getById(1L)).thenReturn(newDebitSubject(1L, true));
            when(subjectService.getById(2L)).thenReturn(newCreditSubject(2L));

            EnterpriseEntity enterprise = new EnterpriseEntity();
            enterprise.setId(1L);
            enterprise.setStartPeriod("202401");
            when(enterpriseMapper.selectById(1L)).thenReturn(enterprise);

            Map<Long, BigDecimal> balances = new HashMap<>();
            balances.put(1L, new BigDecimal("400000"));
            balances.put(2L, new BigDecimal("400000"));

            service.initOpeningBalances(PERIOD, balances);

            verify(enterpriseMapper, never()).updateById(any(EnterpriseEntity.class));
        } finally {
            EnterpriseContextHolder.clear();
        }
    }

    @Test
    @DisplayName("过账前置_早于start_period_throw badRequest")
    void validate_beforeStartPeriod_throws() {
        withEnterpriseContext(1L);
        try {
            PeriodEntity target = newPeriod("open", "none");
            target.setPeriodCode("202101");
            when(periodService.getByPeriodCode("202101")).thenReturn(target);

            EnterpriseEntity enterprise = new EnterpriseEntity();
            enterprise.setId(1L);
            enterprise.setStartPeriod("202401");
            when(enterpriseMapper.selectById(1L)).thenReturn(enterprise);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.validateOpeningBeforePost("202101"));
            assertTrue(ex.getMessage().contains("早于企业建账期间"));
        } finally {
            EnterpriseContextHolder.clear();
        }
    }

    @Test
    @DisplayName("过账前置_等于start_period且未建账_throw badRequest")
    void validate_equalsStartPeriodUnentered_throws() {
        withEnterpriseContext(1L);
        try {
            PeriodEntity target = newPeriod("open", "none");
            target.setPeriodCode("202401");
            when(periodService.getByPeriodCode("202401")).thenReturn(target);

            EnterpriseEntity enterprise = new EnterpriseEntity();
            enterprise.setId(1L);
            enterprise.setStartPeriod("202401");
            when(enterpriseMapper.selectById(1L)).thenReturn(enterprise);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.validateOpeningBeforePost("202401"));
            assertTrue(ex.getMessage().contains("尚未完成期初建账"));
        } finally {
            EnterpriseContextHolder.clear();
        }
    }

    @Test
    @DisplayName("过账前置_等于start_period且已建账_放行")
    void validate_equalsStartPeriodEntered_pass() {
        withEnterpriseContext(1L);
        try {
            PeriodEntity target = newPeriod("open", "entered");
            target.setPeriodCode("202401");
            when(periodService.getByPeriodCode("202401")).thenReturn(target);

            EnterpriseEntity enterprise = new EnterpriseEntity();
            enterprise.setId(1L);
            enterprise.setStartPeriod("202401");
            when(enterpriseMapper.selectById(1L)).thenReturn(enterprise);

            service.validateOpeningBeforePost("202401");
        } finally {
            EnterpriseContextHolder.clear();
        }
    }

    @Test
    @DisplayName("过账前置_晚于start_period_即使未建账也放行")
    void validate_afterStartPeriod_pass() {
        withEnterpriseContext(1L);
        try {
            PeriodEntity target = newPeriod("open", "none");
            target.setPeriodCode("202402");
            when(periodService.getByPeriodCode("202402")).thenReturn(target);

            EnterpriseEntity enterprise = new EnterpriseEntity();
            enterprise.setId(1L);
            enterprise.setStartPeriod("202401");
            when(enterpriseMapper.selectById(1L)).thenReturn(enterprise);

            service.validateOpeningBeforePost("202402");
        } finally {
            EnterpriseContextHolder.clear();
        }
    }

    @Test
    @DisplayName("过账前置_存量企业无start_period_走旧逻辑")
    void validate_legacyNoStartPeriod_usesEarliest() {
        withEnterpriseContext(1L);
        try {
            PeriodEntity target = newPeriod("open", "none");
            target.setPeriodCode(PERIOD);
            when(periodService.getByPeriodCode(PERIOD)).thenReturn(target);

            EnterpriseEntity enterprise = new EnterpriseEntity();
            enterprise.setId(1L);
            when(enterpriseMapper.selectById(1L)).thenReturn(enterprise);
            when(periodService.getOne(any(LambdaQueryWrapper.class))).thenReturn(target);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.validateOpeningBeforePost(PERIOD));
            assertTrue(ex.getMessage().contains("尚未完成期初建账"));
        } finally {
            EnterpriseContextHolder.clear();
        }
    }

    @Test
    @DisplayName("清空期初_清空建账期间且无其他余额_用UpdateWrapper置空start_period")
    void clear_resetsStartPeriodWhenAlone() {
        withEnterpriseContext(1L);
        try {
            when(periodService.getByPeriodCode(PERIOD)).thenReturn(newPeriod("open", "entered"));
            when(voucherMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(subjectBalanceMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(3);

            EnterpriseEntity enterprise = new EnterpriseEntity();
            enterprise.setId(1L);
            enterprise.setStartPeriod(PERIOD);
            when(enterpriseMapper.selectById(1L)).thenReturn(enterprise);
            when(subjectBalanceMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            service.clearOpeningBalances(PERIOD);

            verify(enterpriseMapper).update(isNull(), argThat((UpdateWrapper<EnterpriseEntity> w) ->
                    w.getSqlSet() != null && w.getSqlSet().contains("start_period")));
        } finally {
            EnterpriseContextHolder.clear();
        }
    }

    @Test
    @DisplayName("清空期初_建账期间但其他期间仍有余额_不重置start_period")
    void clear_keepsStartPeriodWhenOthersExist() {
        withEnterpriseContext(1L);
        try {
            when(periodService.getByPeriodCode(PERIOD)).thenReturn(newPeriod("open", "entered"));
            when(voucherMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(subjectBalanceMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(3);

            EnterpriseEntity enterprise = new EnterpriseEntity();
            enterprise.setId(1L);
            enterprise.setStartPeriod(PERIOD);
            when(enterpriseMapper.selectById(1L)).thenReturn(enterprise);
            when(subjectBalanceMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

            service.clearOpeningBalances(PERIOD);

            verify(enterpriseMapper, never()).update(isNull(), any(UpdateWrapper.class));
        } finally {
            EnterpriseContextHolder.clear();
        }
    }

    @Test
    @DisplayName("清空期初_非建账期间_不重置start_period")
    void clear_notStartPeriod_keepsStartPeriod() {
        withEnterpriseContext(1L);
        try {
            when(periodService.getByPeriodCode(PERIOD)).thenReturn(newPeriod("open", "entered"));
            when(voucherMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(subjectBalanceMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(3);

            EnterpriseEntity enterprise = new EnterpriseEntity();
            enterprise.setId(1L);
            enterprise.setStartPeriod("202401");
            when(enterpriseMapper.selectById(1L)).thenReturn(enterprise);

            service.clearOpeningBalances(PERIOD);

            verify(enterpriseMapper, never()).updateById(any(EnterpriseEntity.class));
        } finally {
            EnterpriseContextHolder.clear();
        }
    }

    // ============================================================
    // checkTrialBalance
    // ============================================================

    @Test
    @DisplayName("试算平衡_期间无余额快照_返回全零且balanced=true(当前行为:无法区分无数据与真平衡)")
    void checkTrialBalance_emptySnapshot_returnsAllZeroAndBalanced() {
        // 期间无余额快照行（未过账凭证时正常发生）→ 当前实现返回全零 + balanced=true
        when(subjectBalanceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        Map<String, Object> result = service.checkTrialBalance(PERIOD);

        assertEquals(true, result.get("beginBalanced"));
        assertEquals(true, result.get("movementBalanced"));
        assertEquals(true, result.get("endBalanced"));
        assertEquals(true, result.get("balanced"));
        assertEquals(BigDecimal.ZERO, result.get("totalBeginDebit"));
        assertEquals(BigDecimal.ZERO, result.get("totalBeginCredit"));
        assertEquals(BigDecimal.ZERO, result.get("totalDebitTotal"));
        assertEquals(BigDecimal.ZERO, result.get("totalCreditTotal"));
        assertEquals(BigDecimal.ZERO, result.get("totalEndDebit"));
        assertEquals(BigDecimal.ZERO, result.get("totalEndCredit"));
        // 注意：空数据返回"平衡"是假阳性风险（评估报告 D4），本测试锁定当前行为，修复时需同步更新断言
    }

    @Test
    @DisplayName("试算平衡_期间格式非法_throw badRequest")
    void checkTrialBalance_invalidPeriod_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.checkTrialBalance("2026"));
        assertTrue(ex.getMessage().contains("会计期间格式错误"));
        verifyNoInteractions(subjectBalanceMapper);
    }

    @Test
    @DisplayName("试算平衡_含末级科目_按方向正确汇总")
    void checkTrialBalance_aggregatesByDirection() {
        SubjectBalanceEntity debit = new SubjectBalanceEntity();
        debit.setSubjectId(1L);
        debit.setBeginBalance(new BigDecimal("1000.00"));
        debit.setDebitTotal(new BigDecimal("500.00"));
        debit.setCreditTotal(new BigDecimal("200.00"));
        debit.setEndBalance(new BigDecimal("1300.00"));

        SubjectBalanceEntity credit = new SubjectBalanceEntity();
        credit.setSubjectId(2L);
        credit.setBeginBalance(new BigDecimal("1000.00"));
        credit.setDebitTotal(new BigDecimal("200.00"));
        credit.setCreditTotal(new BigDecimal("500.00"));
        credit.setEndBalance(new BigDecimal("1300.00"));

        when(subjectBalanceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(debit, credit));
        when(subjectService.getById(1L)).thenReturn(newDebitSubject(1L, true));
        when(subjectService.getById(2L)).thenReturn(newCreditSubject(2L));

        Map<String, Object> result = service.checkTrialBalance(PERIOD);

        // 借方科目(1001): 期初1000, 发生借500贷200, 期末1300 → 计入 beginDebit/debitTotal/creditTotal/endDebit
        // 贷方科目(4001): 期初1000, 发生借200贷500, 期末1300 → 计入 beginCredit/debitTotal/creditTotal/endCredit
        assertEquals(new BigDecimal("1000.00"), result.get("totalBeginDebit"));
        assertEquals(new BigDecimal("1000.00"), result.get("totalBeginCredit"));
        assertEquals(new BigDecimal("700.00"), result.get("totalDebitTotal"));
        assertEquals(new BigDecimal("700.00"), result.get("totalCreditTotal"));
        assertEquals(new BigDecimal("1300.00"), result.get("totalEndDebit"));
        assertEquals(new BigDecimal("1300.00"), result.get("totalEndCredit"));
        assertEquals(true, result.get("beginBalanced"));
        assertEquals(true, result.get("movementBalanced"));
        assertEquals(true, result.get("endBalanced"));
    }
}
