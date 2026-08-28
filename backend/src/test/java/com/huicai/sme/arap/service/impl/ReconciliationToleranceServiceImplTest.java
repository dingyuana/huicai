package com.huicai.sme.arap.service.impl;

import com.huicai.sme.arap.entity.ReconciliationToleranceEntity;
import com.huicai.sme.arap.mapper.ReconciliationToleranceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReconciliationToleranceServiceImplTest {

    @Mock private ReconciliationToleranceMapper toleranceMapper;
    private ReconciliationToleranceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReconciliationToleranceServiceImpl(toleranceMapper);
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

    @Test
    void getTolerance_customerSpecific_absolute() {
        when(toleranceMapper.findTolerance(eq(1L), eq(100L), eq("CUSTOMER")))
                .thenReturn(stub(1L, 100L, "CUSTOMER", new BigDecimal("10"), "ABSOLUTE"));
        ReconciliationToleranceEntity result = service.getTolerance(100L, "CUSTOMER");
        assertEquals(BigDecimal.TEN, result.getToleranceAmount());
        assertNull(result.getToleranceRate());
    }

    @Test
    void getTolerance_noConfig_returnsDefaultFallback() {
        when(toleranceMapper.findTolerance(eq(1L), eq(999L), eq("CUSTOMER"))).thenReturn(null);
        ReconciliationToleranceEntity result = service.getTolerance(999L, "CUSTOMER");
        assertEquals(new BigDecimal("5.00"), result.getToleranceAmount());
        assertEquals("ABSOLUTE", result.getToleranceType());
    }

    @Test
    void getTolerance_percentConfig() {
        when(toleranceMapper.findTolerance(eq(1L), eq(100L), eq("VENDOR")))
                .thenReturn(stub(1L, 100L, "VENDOR", new BigDecimal("2.50"), "PERCENT"));
        ReconciliationToleranceEntity result = service.getTolerance(100L, "VENDOR");
        assertNull(result.getToleranceAmount());
        assertEquals(new BigDecimal("2.50"), result.getToleranceRate());
    }

    @Test
    void getToleranceAmount_absolute() {
        when(toleranceMapper.findTolerance(eq(1L), eq(100L), eq("CUSTOMER")))
                .thenReturn(stub(1L, 100L, "CUSTOMER", new BigDecimal("10.00"), "ABSOLUTE"));
        assertEquals(new BigDecimal("10.00"), service.getToleranceAmount(100L, "CUSTOMER"));
    }

    @Test
    void getToleranceAmount_percent_fallsBackToDefaultAmount() {
        when(toleranceMapper.findTolerance(eq(1L), eq(100L), eq("VENDOR")))
                .thenReturn(stub(1L, 100L, "VENDOR", new BigDecimal("3.50"), "PERCENT"));
        // PERCENT config has toleranceAmount=null, falls to DEFAULT_TOLERANCE_VALUE=5.00
        assertEquals(new BigDecimal("5.00"), service.getToleranceAmount(100L, "VENDOR"));
    }

    @Test
    void getToleranceAmount_customerMissing_returnsGlobal() {
        when(toleranceMapper.findTolerance(eq(1L), eq(999L), eq("CUSTOMER"))).thenReturn(null);
        assertEquals(new BigDecimal("5.00"), service.getToleranceAmount(999L, "CUSTOMER"));
    }
}
