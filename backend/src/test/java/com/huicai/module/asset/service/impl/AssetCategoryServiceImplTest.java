package com.huicai.sme.asset.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.sme.asset.entity.AssetCategoryEntity;
import com.huicai.sme.asset.mapper.AssetCategoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetCategoryServiceImplTest {

    @Mock private AssetCategoryMapper mapper;
    @InjectMocks private AssetCategoryServiceImpl service;

    private AssetCategoryEntity stubEntity() {
        AssetCategoryEntity e = new AssetCategoryEntity();
        e.setId(1L);
        e.setCode("ASSET-001");
        e.setName("办公设备");
        e.setDepreciationMethod("LINEAR");
        e.setUsefulLife(5);
        e.setResidualRate(BigDecimal.valueOf(0.05));
        return e;
    }

    @Test
    void getById_存在_返回Entity() {
        when(mapper.selectById(1L)).thenReturn(stubEntity());
        AssetCategoryEntity result = service.getById(1L);
        assertNotNull(result);
    }

    @Test
    void getById_不存在_抛BusinessException() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getById(99L));
    }

    @Test
    void create_正常_调insert() {
        service.create(stubEntity());
        verify(mapper).insert(any(AssetCategoryEntity.class));
    }

    @Test
    void update_存在_调updateById() {
        AssetCategoryEntity entity = stubEntity();
        when(mapper.selectById(entity.getId())).thenReturn(entity);
        service.update(entity);
        verify(mapper).updateById(any(AssetCategoryEntity.class));
    }

    @Test
    void update_不存在_抛BusinessException() {
        when(mapper.selectById(99L)).thenReturn(null);
        AssetCategoryEntity entity = stubEntity();
        entity.setId(99L);
        assertThrows(BusinessException.class, () -> service.update(entity));
    }

    @Test
    void delete_正常_调deleteById() {
        service.delete(1L);
        verify(mapper).deleteById(1L);
    }

    @Test
    void pageQuery_调selectPage() {
        when(mapper.selectPage(any(), any())).thenReturn(null);
        service.pageQuery(null, 1, 20);
        verify(mapper).selectPage(any(), any());
    }
}
