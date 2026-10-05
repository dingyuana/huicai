package com.huicai.sme.arap.service.impl;

import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.arap.entity.ReconciliationToleranceEntity;
import com.huicai.sme.arap.mapper.ReconciliationToleranceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReconciliationToleranceServiceImplTest {

    /** P106 / AT-106-3：非默认企业号。原实现写死企业 1，夹具曾把该缺陷值当成期望值。 */
    private static final Long CTX_ENTERPRISE = 990301L;

    @Mock private ReconciliationToleranceMapper toleranceMapper;
    private ReconciliationToleranceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReconciliationToleranceServiceImpl(toleranceMapper);
        EnterpriseContextHolder.set(CTX_ENTERPRISE);
    }

    @AfterEach
    void tearDown() {
        EnterpriseContextHolder.clear();
    }

    private ReconciliationToleranceEntity stub(Long id, Long partyId, String partyType,
            BigDecimal toleranceValue, String toleranceType) {
        ReconciliationToleranceEntity e = new ReconciliationToleranceEntity();
        e.setId(id);
        e.setPartyId(partyId);
        e.setPartyType(partyType);
        e.setToleranceValue(toleranceValue);
        e.setToleranceType(toleranceType);
        e.setDeleted(0);
        return e;
    }

    /**
     * P106 / AT-106-3：容差查询必须按<b>当前企业上下文</b>，不得写死企业 1。
     *
     * <p><b>原夹具把缺陷值当期望值</b>（AGENTS §4.3 第 11 条）：原实现用
     * {@code DEFAULT_ENTERPRISE_ID = 1L}，测试因此 stub {@code findTolerance(eq(CTX_ENTERPRISE), ...)}
     * ——「生产写错、测试也写错」互相印证，看起来自洽。
     */
    @Test
    @DisplayName("AT-106-3 容差查询按当前企业上下文而非企业 1")
    void getTolerance_mustUseCurrentEnterpriseContext() {
        when(toleranceMapper.findTolerance(eq(CTX_ENTERPRISE), eq(100L), eq("CUSTOMER")))
                .thenReturn(stub(1L, 100L, "CUSTOMER", new BigDecimal("77"), "ABSOLUTE"));

        ReconciliationToleranceEntity result = service.getTolerance(100L, "CUSTOMER");

        assertEquals(new BigDecimal("77"), result.getToleranceAmount(),
                "应返回当前企业 " + CTX_ENTERPRISE + " 的容差配置");
        verify(toleranceMapper, never()).findTolerance(eq(1L), any(), any());
    }

    @Test
    @DisplayName("负向：无企业上下文时必须抛 BusinessException，不得静默回落企业 1")
    void getTolerance_withoutContext_shouldThrowNotFallbackToEnterpriseOne() {
        EnterpriseContextHolder.clear();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getTolerance(100L, "CUSTOMER"),
                "【负向】取不到企业上下文时不得回落企业 1（铁律 #14：异常须明确指出缺失原因）");

        assertTrue(ex.getMessage().contains("企业"),
                "异常信息应指明是「企业上下文」缺失，实际: " + ex.getMessage());
        verify(toleranceMapper, never()).findTolerance(any(), any(), any());
    }

    @Test
    void getTolerance_customerSpecific_absolute() {
        when(toleranceMapper.findTolerance(eq(CTX_ENTERPRISE), eq(100L), eq("CUSTOMER")))
                .thenReturn(stub(1L, 100L, "CUSTOMER", new BigDecimal("10"), "ABSOLUTE"));
        ReconciliationToleranceEntity result = service.getTolerance(100L, "CUSTOMER");
        assertEquals(BigDecimal.TEN, result.getToleranceAmount());
        assertNull(result.getToleranceRate());
    }

    @Test
    void getTolerance_noConfig_returnsDefaultFallback() {
        when(toleranceMapper.findTolerance(eq(CTX_ENTERPRISE), eq(999L), eq("CUSTOMER"))).thenReturn(null);
        ReconciliationToleranceEntity result = service.getTolerance(999L, "CUSTOMER");
        assertEquals(new BigDecimal("5.00"), result.getToleranceAmount());
        assertEquals("ABSOLUTE", result.getToleranceType());
    }

    @Test
    void getTolerance_percentConfig() {
        when(toleranceMapper.findTolerance(eq(CTX_ENTERPRISE), eq(100L), eq("VENDOR")))
                .thenReturn(stub(1L, 100L, "VENDOR", new BigDecimal("2.50"), "PERCENT"));
        ReconciliationToleranceEntity result = service.getTolerance(100L, "VENDOR");
        assertNull(result.getToleranceAmount());
        assertEquals(new BigDecimal("2.50"), result.getToleranceRate());
    }

    @Test
    void getToleranceAmount_absolute() {
        when(toleranceMapper.findTolerance(eq(CTX_ENTERPRISE), eq(100L), eq("CUSTOMER")))
                .thenReturn(stub(1L, 100L, "CUSTOMER", new BigDecimal("10.00"), "ABSOLUTE"));
        assertEquals(new BigDecimal("10.00"), service.getToleranceAmount(100L, "CUSTOMER"));
    }

    @Test
    void getToleranceAmount_percent_fallsBackToDefaultAmount() {
        when(toleranceMapper.findTolerance(eq(CTX_ENTERPRISE), eq(100L), eq("VENDOR")))
                .thenReturn(stub(1L, 100L, "VENDOR", new BigDecimal("3.50"), "PERCENT"));
        // PERCENT config has toleranceAmount=null, falls to DEFAULT_TOLERANCE_VALUE=5.00
        assertEquals(new BigDecimal("5.00"), service.getToleranceAmount(100L, "VENDOR"));
    }

    @Test
    void getToleranceAmount_customerMissing_returnsGlobal() {
        when(toleranceMapper.findTolerance(eq(CTX_ENTERPRISE), eq(999L), eq("CUSTOMER"))).thenReturn(null);
        assertEquals(new BigDecimal("5.00"), service.getToleranceAmount(999L, "CUSTOMER"));
    }
}
