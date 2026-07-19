package com.huicai.base.system.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.base.system.entity.DeptEntity;
import com.huicai.base.system.mapper.DeptMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeptServiceImplTest {

    @Mock private DeptMapper deptMapper;
    @InjectMocks private DeptServiceImpl service;

    private DeptEntity stubEntity() {
        DeptEntity e = new DeptEntity();
        e.setId(1L);
        e.setName("测试部门");
        e.setParentId(null);
        e.setSortOrder(1);
        e.setStatus("ACTIVE");
        return e;
    }

    @Test
    void getById_存在_返回Entity() {
        when(deptMapper.selectById(1L)).thenReturn(stubEntity());
        DeptEntity result = service.getById(1L);
        assertNotNull(result);
        assertEquals("测试部门", result.getName());
    }

    @Test
    void getById_不存在_返回Null() {
        when(deptMapper.selectById(99L)).thenReturn(null);
        assertNull(service.getById(99L));
    }

    @Test
    void create_正常_调insert() {
        service.create(stubEntity());
        verify(deptMapper).insert(any(DeptEntity.class));
    }

    @Test
    void update_正常_调updateById() {
        service.update(stubEntity());
        verify(deptMapper).updateById(any(DeptEntity.class));
    }

    @Test
    void delete_无子部门_调deleteById() {
        when(deptMapper.selectCount(any())).thenReturn(0L);
        service.delete(1L);
        verify(deptMapper).deleteById(1L);
    }

    @Test
    void delete_有子部门_抛BusinessException() {
        when(deptMapper.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.delete(1L));
        verify(deptMapper, never()).deleteById(anyLong());
    }

    @Test
    void getDeptTree_调selectList() {
        when(deptMapper.selectList(any())).thenReturn(java.util.Collections.singletonList(stubEntity()));
        service.getDeptTree();
        verify(deptMapper).selectList(any());
    }
}