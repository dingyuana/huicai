package com.huicai.module.arap.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.dto.PayableVO;
import com.huicai.module.arap.entity.PayableEntity;
import com.huicai.module.arap.mapper.PayableMapper;
import com.huicai.module.arap.mapper.VendorMapper;
import com.huicai.module.system.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayableServiceImplTest {

    @Mock private PayableMapper mapper;
    @Mock private VendorMapper vendorMapper;
    @Mock private UserMapper userMapper;

    @InjectMocks private PayableServiceImpl service;

    @Test
    void pageQuery_带vendorId和period_调selectPage() {
        Page<PayableEntity> emptyPage = new Page<>(1, 20, 0);
        when(mapper.selectPage(any(), any())).thenReturn(emptyPage);
        IPage<PayableVO> r = service.pageQuery(1L, "202606", null, null, null, 1, 20);
        assertNotNull(r);
        assertEquals(0, r.getTotal());
        verify(mapper).selectPage(any(), any());
    }

    @Test
    void pageQuery_无参_走默认值() {
        Page<PayableEntity> emptyPage = new Page<>(1, 20, 0);
        when(mapper.selectPage(any(), any())).thenReturn(emptyPage);
        IPage<PayableVO> r = service.pageQuery(null, null, null, null, null, 1, 20);
        assertNotNull(r);
        assertEquals(0, r.getTotal());
        verify(mapper).selectPage(any(), any());
    }

    @Test
    void getById_存在_返回entity() {
        PayableEntity p = new PayableEntity();
        p.setId(1L);
        when(mapper.selectById(1L)).thenReturn(p);
        PayableVO r = service.getById(1L);
        assertNotNull(r);
        assertEquals(1L, r.getId());
    }

    @Test
    void getById_不存在_throw() {
        when(mapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(99L));
        assertTrue(ex.getMessage().contains("应付明细不存在"));
    }

    @Test
    void create_settledAmount为null_默认0_unsettled自动计算() {
        PayableEntity p = new PayableEntity();
        p.setAmount(new BigDecimal("1000"));
        // settledAmount null
        PayableEntity r = service.create(p);
        assertEquals(0, BigDecimal.ZERO.compareTo(r.getSettledAmount()));
        assertEquals(0, new BigDecimal("1000").compareTo(r.getUnsettledAmount()));
        verify(mapper).insert(p);
    }

    @Test
    void agingAnalysis_无数据_全部bucket为0() {
        when(mapper.agingByVendor(1L)).thenReturn(List.of());
        Map<String, Object> r = service.agingAnalysis(1L);
        assertEquals(7, ((java.util.Set<?>) r.get("buckets")).size());
        assertEquals(0, ((BigDecimal) r.get("total")).compareTo(BigDecimal.ZERO));
    }

    @Test
    void agingAnalysis_有数据_填入对应bucket() {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("aging_bucket", "days_31_60");
        row.put("amount", new BigDecimal("500"));
        row.put("count", 2);
        when(mapper.agingByVendor(1L)).thenReturn(List.of(row));

        Map<String, Object> r = service.agingAnalysis(1L);
        assertEquals(0, new BigDecimal("500").compareTo((BigDecimal) r.get("total")));
    }
}
