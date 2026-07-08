package com.huicai.module.arap.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.dto.ReconciliationToleranceDTO;
import com.huicai.module.arap.dto.vo.ReconciliationToleranceVO;
import com.huicai.module.arap.entity.ReconciliationToleranceEntity;
import com.huicai.module.arap.mapper.ReconciliationToleranceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationToleranceServiceImplTest {

    @Mock private ReconciliationToleranceMapper toleranceMapper;

    @InjectMocks private ReconciliationToleranceServiceImpl service;

    private ReconciliationToleranceEntity stubTolerance(Long id, Long partyId, String partyType, BigDecimal amount, BigDecimal rate) {
        ReconciliationToleranceEntity e = new ReconciliationToleranceEntity();
        e.setId(id);
        e.setPartyId(partyId);
        e.setPartyType(partyType);
        e.setToleranceAmount(amount);
        e.setToleranceRate(rate);
        e.setEffectiveFrom(LocalDate.now().minusDays(1));
        e.setDeleted(0);
        return e;
    }

    @Test
    void getTolerance_客户专属配置_返回专属配置() {
        when(toleranceMapper.findTolerance(anyLong(), eq(100L), eq("CUSTOMER"), any())).thenReturn(stubTolerance(1L, 100L, "CUSTOMER", new BigDecimal("10.00"), new BigDecimal("5.00")));

        ReconciliationToleranceEntity result = service.getTolerance(100L, "CUSTOMER");

        assertEquals(new BigDecimal("10.00"), result.getToleranceAmount());
        assertEquals(new BigDecimal("5.00"), result.getToleranceRate());
    }

    @Test
    void getTolerance_无专属配置_返回全局配置() {
        when(toleranceMapper.findTolerance(anyLong(), eq(999L), eq("CUSTOMER"), any())).thenReturn(stubTolerance(1L, null, null, new BigDecimal("5.00"), new BigDecimal("10.00")));

        ReconciliationToleranceEntity result = service.getTolerance(999L, "CUSTOMER");

        assertEquals(new BigDecimal("5.00"), result.getToleranceAmount());
        assertEquals(new BigDecimal("10.00"), result.getToleranceRate());
    }

    @Test
    void getTolerance_无任何配置_返回默认值() {
        when(toleranceMapper.findTolerance(anyLong(), eq(999L), eq("CUSTOMER"), any())).thenReturn(null);

        ReconciliationToleranceEntity result = service.getTolerance(999L, "CUSTOMER");

        assertEquals(new BigDecimal("5.00"), result.getToleranceAmount());
        assertEquals(new BigDecimal("10.00"), result.getToleranceRate());
    }

    @Test
    void getToleranceAmount_客户专属_返回专属金额() {
        when(toleranceMapper.findTolerance(anyLong(), eq(100L), eq("CUSTOMER"), any())).thenReturn(stubTolerance(1L, 100L, "CUSTOMER", new BigDecimal("15.00"), new BigDecimal("10.00")));

        BigDecimal result = service.getToleranceAmount(100L, "CUSTOMER");

        assertEquals(new BigDecimal("15.00"), result);
    }

    @Test
    void getDefaultConfig_存在全局配置_返回全局配置() {
        when(toleranceMapper.selectOne(any())).thenReturn(stubTolerance(1L, null, null, new BigDecimal("5.00"), new BigDecimal("10.00")));

        ReconciliationToleranceVO result = service.getDefaultConfig();

        assertNotNull(result);
        assertEquals(new BigDecimal("5.00"), result.getToleranceAmount());
        assertNull(result.getPartyId());
    }

    @Test
    void getDefaultConfig_不存在全局配置_抛异常() {
        when(toleranceMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.getDefaultConfig());
    }

    @Test
    void create_创建客户专属配置_成功() {
        ReconciliationToleranceDTO dto = new ReconciliationToleranceDTO();
        dto.setPartyId(100L);
        dto.setPartyType("CUSTOMER");
        dto.setToleranceAmount(new BigDecimal("20.00"));
        dto.setToleranceRate(new BigDecimal("8.00"));

        when(toleranceMapper.insert(any(ReconciliationToleranceEntity.class))).thenAnswer(invocation -> {
            ReconciliationToleranceEntity e = invocation.getArgument(0);
            e.setId(1L);
            return 1;
        });

        ReconciliationToleranceVO result = service.create(dto);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(100L, result.getPartyId());
        assertEquals("CUSTOMER", result.getPartyType());
        assertEquals(new BigDecimal("20.00"), result.getToleranceAmount());
    }

    @Test
    void update_更新配置_成功() {
        ReconciliationToleranceEntity existing = stubTolerance(1L, 100L, "CUSTOMER", new BigDecimal("10.00"), new BigDecimal("10.00"));
        when(toleranceMapper.selectById(1L)).thenReturn(existing);
        when(toleranceMapper.updateById(any(ReconciliationToleranceEntity.class))).thenReturn(1);

        ReconciliationToleranceDTO dto = new ReconciliationToleranceDTO();
        dto.setToleranceAmount(new BigDecimal("25.00"));

        ReconciliationToleranceVO result = service.update(1L, dto);

        assertEquals(new BigDecimal("25.00"), result.getToleranceAmount());
        assertEquals(new BigDecimal("10.00"), result.getToleranceRate());
    }

    @Test
    void update_配置不存在_抛异常() {
        when(toleranceMapper.selectById(1L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.update(1L, new ReconciliationToleranceDTO()));
    }

    @Test
    void delete_删除客户配置_成功() {
        ReconciliationToleranceEntity existing = stubTolerance(1L, 100L, "CUSTOMER", new BigDecimal("10.00"), new BigDecimal("10.00"));
        when(toleranceMapper.selectById(1L)).thenReturn(existing);
        when(toleranceMapper.updateById(any(ReconciliationToleranceEntity.class))).thenReturn(1);

        assertDoesNotThrow(() -> service.delete(1L));

        assertEquals(1, existing.getDeleted());
    }

    @Test
    void delete_全局配置_抛异常() {
        ReconciliationToleranceEntity global = stubTolerance(1L, null, null, new BigDecimal("5.00"), new BigDecimal("10.00"));
        when(toleranceMapper.selectById(1L)).thenReturn(global);

        assertThrows(BusinessException.class, () -> service.delete(1L));
    }

    @Test
    void delete_配置不存在_抛异常() {
        when(toleranceMapper.selectById(1L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.delete(1L));
    }
}
