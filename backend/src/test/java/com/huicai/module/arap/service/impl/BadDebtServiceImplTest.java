package com.huicai.module.arap.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.entity.BadDebtProvisionEntity;
import com.huicai.module.arap.entity.ReceivableEntity;
import com.huicai.module.arap.mapper.BadDebtProvisionMapper;
import com.huicai.module.arap.mapper.ReceivableMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BadDebtServiceImplTest {

    @Mock private BadDebtProvisionMapper mapper;
    @Mock private ReceivableMapper receivableMapper;

    @InjectMocks private BadDebtServiceImpl service;

    private ReceivableEntity stubRec(Long id, BigDecimal unsettled, LocalDate dueDate) {
        ReceivableEntity r = new ReceivableEntity();
        r.setId(id);
        r.setAmount(unsettled);
        r.setUnsettledAmount(unsettled);
        r.setDueDate(dueDate);
        return r;
    }

    @Test
    void pageQuery_带status_调selectPage() {
        when(mapper.selectPage(any(), any())).thenReturn(null);
        IPage<BadDebtProvisionEntity> r = service.pageQuery("DRAFT", 1, 20);
        assertNull(r);
        verify(mapper).selectPage(any(), any());
    }

    @Test
    void getById_存在_返回entity() {
        BadDebtProvisionEntity e = new BadDebtProvisionEntity();
        e.setId(1L);
        when(mapper.selectById(1L)).thenReturn(e);
        BadDebtProvisionEntity r = service.getById(1L);
        assertNotNull(r);
    }

    @Test
    void getById_不存在_throw() {
        when(mapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(99L));
        assertTrue(ex.getMessage().contains("坏账准备记录不存在"));
    }

    @Test
    void provisionByAging_无应收_总额为0() {
        when(receivableMapper.selectList(any())).thenReturn(List.of());
        BadDebtProvisionEntity r = service.provisionByAging("202606", Map.of("current", new BigDecimal("0.05")));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.getTotalAmount()));
        assertEquals("DRAFT", r.getStatus());
        assertEquals("AGING_RATIO", r.getMethod());
        verify(mapper).insert(r);
    }

    @Test
    void provisionByAging_有应收_按ratio计算() {
        LocalDate today = LocalDate.now();
        LocalDate due40 = today.minusDays(40);  // 落入 days_31_60
        when(receivableMapper.selectList(any())).thenReturn(List.of(
                stubRec(1L, new BigDecimal("1000"), due40)
        ));
        Map<String, BigDecimal> ratios = new HashMap<>();
        ratios.put("days_31_60", new BigDecimal("0.10"));

        BadDebtProvisionEntity r = service.provisionByAging("202606", ratios);
        assertEquals(0, new BigDecimal("100.00").compareTo(r.getTotalAmount()));
    }

    @Test
    void provisionByPercentage_3条应收_余额合计乘ratio() {
        when(receivableMapper.selectList(any())).thenReturn(List.of(
                stubRec(1L, new BigDecimal("1000"), null),
                stubRec(2L, new BigDecimal("2000"), null),
                stubRec(3L, new BigDecimal("3000"), null)
        ));
        BadDebtProvisionEntity r = service.provisionByPercentage("202606", new BigDecimal("0.05"));
        // 6000 × 0.05 = 300
        assertEquals(0, new BigDecimal("300.00").compareTo(r.getTotalAmount()));
        assertEquals("PERCENTAGE", r.getMethod());
    }

    @Test
    void confirm_DRAFT_改CONFIRMED() {
        BadDebtProvisionEntity e = new BadDebtProvisionEntity();
        e.setId(1L);
        e.setStatus("DRAFT");
        when(mapper.selectById(1L)).thenReturn(e);
        BadDebtProvisionEntity r = service.confirm(1L);
        assertEquals("CONFIRMED", r.getStatus());
        verify(mapper).updateById(e);
    }

    @Test
    void confirm_非DRAFT_throw() {
        BadDebtProvisionEntity e = new BadDebtProvisionEntity();
        e.setId(1L);
        e.setStatus("CONFIRMED");
        when(mapper.selectById(1L)).thenReturn(e);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.confirm(1L));
        assertTrue(ex.getMessage().contains("仅草稿"));
    }

    @Test
    void delete_DRAFT_成功删除() {
        BadDebtProvisionEntity e = new BadDebtProvisionEntity();
        e.setId(1L);
        e.setStatus("DRAFT");
        when(mapper.selectById(1L)).thenReturn(e);
        service.delete(1L);
        verify(mapper).deleteById(1L);
    }

    @Test
    void delete_非DRAFT_throw() {
        BadDebtProvisionEntity e = new BadDebtProvisionEntity();
        e.setId(1L);
        e.setStatus("CONFIRMED");
        when(mapper.selectById(1L)).thenReturn(e);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(1L));
        assertTrue(ex.getMessage().contains("仅草稿"));
    }
}
