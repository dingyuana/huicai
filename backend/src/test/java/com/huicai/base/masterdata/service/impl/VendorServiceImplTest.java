package com.huicai.base.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.base.masterdata.entity.VendorEntity;
import com.huicai.base.masterdata.mapper.VendorMapper;
import com.huicai.base.business.mapper.BusinessDocMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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

    @Mock
    private VendorMapper mapper;

    @Mock
    private BusinessDocMapper businessDocMapper;

    @InjectMocks
    private VendorServiceImpl service;

    @Captor
    private ArgumentCaptor<VendorEntity> entityCaptor;

    private VendorEntity stubVendor(Long id, String code, String name) {
        VendorEntity e = new VendorEntity();
        e.setId(id);
        e.setCode(code);
        e.setName(name);
        e.setIsActive(true);
        e.setCreditLimit(BigDecimal.ZERO);
        e.setCreditDays(30);
        return e;
    }

    // ===================== getById =====================

    @Test
    @DisplayName("getById - 存在返回实体")
    void getById_存在_返回实体() {
        when(mapper.selectById(1L)).thenReturn(stubVendor(1L, "V001", "供应商A"));
        VendorEntity r = service.getById(1L);
        assertNotNull(r);
        assertEquals("V001", r.getCode());
        assertEquals("供应商A", r.getName());
    }

    @Test
    @DisplayName("getById - 不存在抛BusinessException")
    void getById_不存在_抛异常() {
        when(mapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(99L));
        assertTrue(ex.getMessage().contains("供应商不存在"));
    }

    // ===================== create =====================

    @Test
    @DisplayName("create - 成功创建")
    void create_成功创建() {
        VendorEntity entity = stubVendor(null, "V001", "供应商A");
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(VendorEntity.class))).thenReturn(1);
        VendorEntity r = service.create(entity);
        assertNotNull(r);
        verify(mapper).insert(any(VendorEntity.class));
    }

    @Test
    @DisplayName("create - 编码重复抛异常")
    void create_编码重复抛异常() {
        VendorEntity entity = stubVendor(null, "V001", "供应商A");
        when(mapper.selectCount(any())).thenReturn(1L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(entity));
        assertTrue(ex.getMessage().contains("供应商编码已存在"));
    }

    @Test
    @DisplayName("create - 默认值填充(isActive=true, creditLimit=0, creditDays=30)")
    void create_默认值填充() {
        VendorEntity entity = new VendorEntity();
        entity.setCode("V001");
        entity.setName("供应商A");
        // isActive / creditLimit / creditDays 均为 null，验证被填充默认值
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(VendorEntity.class))).thenReturn(1);
        VendorEntity r = service.create(entity);
        assertTrue(r.getIsActive());
        assertEquals(BigDecimal.ZERO, r.getCreditLimit());
        assertEquals(30, r.getCreditDays());
        verify(mapper).insert(any(VendorEntity.class));
    }

    // ===================== update =====================

    @Test
    @DisplayName("update - 更新成功")
    void update_更新成功() {
        VendorEntity existing = stubVendor(1L, "V001", "供应商A");
        VendorEntity input = stubVendor(1L, "V002", "供应商B");
        when(mapper.selectById(1L)).thenReturn(existing);
        when(mapper.selectCount(any())).thenReturn(0L);
        service.update(input);
        verify(mapper).updateById(entityCaptor.capture());
        VendorEntity captured = entityCaptor.getValue();
        assertEquals("V002", captured.getCode());
        assertEquals("供应商B", captured.getName());
        // 验证 updateById 的入参是 existing 对象（被修改后的）
        assertSame(existing, captured);
    }

    @Test
    @DisplayName("update - 不存在抛异常")
    void update_不存在抛异常() {
        VendorEntity input = stubVendor(99L, "V001", "供应商A");
        when(mapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(input));
        assertTrue(ex.getMessage().contains("供应商不存在"));
        verify(mapper, never()).updateById(any(VendorEntity.class));
    }

    // ===================== delete =====================

    @Test
    @DisplayName("delete - 删除成功")
    void delete_成功删除() {
        service.delete(1L);
        verify(mapper).deleteById(1L);
    }

    // ===================== listAll =====================

    @Test
    @DisplayName("listAll - 返回激活供应商列表")
    void listAll_返回激活列表() {
        when(mapper.selectList(any())).thenReturn(List.of(
                stubVendor(1L, "V001", "供应商A"),
                stubVendor(2L, "V002", "供应商B")
        ));
        List<VendorEntity> r = service.listAll();
        assertEquals(2, r.size());
        verify(mapper).selectList(any());
    }

    // ===================== pageQuery =====================

    @Test
    @DisplayName("pageQuery - 关键字搜索")
    void pageQuery_关键字搜索() {
        @SuppressWarnings("unchecked")
        Page<VendorEntity> mockPage = mock(Page.class);
        when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);
        IPage<VendorEntity> r = service.pageQuery("供应商", null, 1, 20);
        assertNotNull(r);
        verify(mapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("pageQuery - 状态过滤（默认分页参数）")
    void pageQuery_状态过滤() {
        @SuppressWarnings("unchecked")
        Page<VendorEntity> mockPage = mock(Page.class);
        when(mapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);
        IPage<VendorEntity> r = service.pageQuery(null, true, null, null);
        assertNotNull(r);
        verify(mapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    // ===================== unsettledSummary =====================

    @Test
    @DisplayName("unsettledSummary - 返回汇总数据")
    void unsettledSummary_返回汇总() {
        Map<String, Object> row = Map.of("vendorId", 1L, "totalAmount", BigDecimal.valueOf(1000));
        when(businessDocMapper.aggregateByVendor()).thenReturn(List.of(row));
        List<Map<String, Object>> r = service.unsettledSummary();
        assertEquals(1, r.size());
        assertEquals(1L, r.get(0).get("vendorId"));
        verify(businessDocMapper).aggregateByVendor();
    }
}