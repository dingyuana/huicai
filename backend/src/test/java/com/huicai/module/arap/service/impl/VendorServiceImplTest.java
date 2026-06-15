package com.huicai.module.arap.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.entity.VendorEntity;
import com.huicai.module.arap.mapper.PayableMapper;
import com.huicai.module.arap.mapper.VendorMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VendorServiceImplTest {

    @Mock private VendorMapper mapper;
    @Mock private PayableMapper payableMapper;

    @InjectMocks private VendorServiceImpl service;

    private VendorEntity stubVendor(Long id, String code, String name) {
        VendorEntity v = new VendorEntity();
        v.setId(id);
        v.setCode(code);
        v.setName(name);
        v.setIsActive(true);
        v.setCreditLimit(BigDecimal.ZERO);
        v.setCreditDays(30);
        return v;
    }

    @Test
    void pageQuery_带keyword_调selectPage() {
        when(mapper.selectPage(any(), any())).thenReturn(null);
        IPage<VendorEntity> r = service.pageQuery("abc", true, 1, 20);
        assertNull(r);
        verify(mapper).selectPage(any(), any());
    }

    @Test
    void listAll_调selectList() {
        when(mapper.selectList(any())).thenReturn(List.of(stubVendor(1L, "V001", "供应商A")));
        List<VendorEntity> r = service.listAll();
        assertEquals(1, r.size());
    }

    @Test
    void getById_存在_返回entity() {
        when(mapper.selectById(1L)).thenReturn(stubVendor(1L, "V001", "供应商A"));
        VendorEntity r = service.getById(1L);
        assertNotNull(r);
    }

    @Test
    void getById_不存在_throw() {
        when(mapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(99L));
        assertTrue(ex.getMessage().contains("供应商不存在"));
    }

    @Test
    void create_编码重复_throw() {
        VendorEntity v = stubVendor(null, "V001", "供应商A");
        when(mapper.selectCount(any())).thenReturn(1L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(v));
        assertTrue(ex.getMessage().contains("已存在"));
    }

    @Test
    void create_编码唯一_默认值填充() {
        VendorEntity v = new VendorEntity();
        v.setCode("V001");
        v.setName("供应商A");
        when(mapper.selectCount(any())).thenReturn(0L);

        VendorEntity r = service.create(v);
        assertEquals(true, r.getIsActive());
        assertEquals(0, BigDecimal.ZERO.compareTo(r.getCreditLimit()));
        assertEquals(30, r.getCreditDays());
    }

    @Test
    void update_正常_字段被覆盖() {
        VendorEntity existing = stubVendor(1L, "OLD", "老名");
        VendorEntity update = stubVendor(1L, "NEW", "新名");
        when(mapper.selectById(1L)).thenReturn(existing);
        when(mapper.selectCount(any())).thenReturn(0L);

        VendorEntity r = service.update(update);
        assertEquals("NEW", r.getCode());
        assertEquals("新名", r.getName());
    }

    @Test
    void unsettledSummary_调aggregateByVendor() {
        when(payableMapper.aggregateByVendor()).thenReturn(List.of());
        List<Map<String, Object>> r = service.unsettledSummary();
        assertNotNull(r);
        verify(payableMapper).aggregateByVendor();
    }
}
