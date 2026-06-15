package com.huicai.module.arap.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.entity.EmployeeEntity;
import com.huicai.module.arap.mapper.EmployeeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    @Mock private EmployeeMapper mapper;
    @InjectMocks private EmployeeServiceImpl service;

    private EmployeeEntity stubEmp(Long id, String code, String name) {
        EmployeeEntity e = new EmployeeEntity();
        e.setId(id);
        e.setCode(code);
        e.setName(name);
        e.setIsActive(true);
        return e;
    }

    @Test
    void pageQuery_带keyword_调selectPage() {
        when(mapper.selectPage(any(), any())).thenReturn(null);
        IPage<EmployeeEntity> r = service.pageQuery("abc", true, 1, 20);
        assertNull(r);
        verify(mapper).selectPage(any(), any());
    }

    @Test
    void listAll_调selectList() {
        when(mapper.selectList(any())).thenReturn(List.of(stubEmp(1L, "E001", "员工A")));
        List<EmployeeEntity> r = service.listAll();
        assertEquals(1, r.size());
        verify(mapper).selectList(any());
    }

    @Test
    void getById_存在_返回entity() {
        when(mapper.selectById(1L)).thenReturn(stubEmp(1L, "E001", "员工A"));
        EmployeeEntity r = service.getById(1L);
        assertNotNull(r);
        assertEquals("E001", r.getCode());
    }

    @Test
    void getById_不存在_throw() {
        when(mapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(99L));
        assertTrue(ex.getMessage().contains("员工不存在"));
    }

    @Test
    void findByName_全名命中_返回entity() {
        when(mapper.selectList(any())).thenReturn(List.of(stubEmp(1L, "E001", "张三")));
        EmployeeEntity r = service.findByName("张三");
        assertNotNull(r);
        assertEquals(1L, r.getId());
    }

    @Test
    void findByName_全名未中_模糊命中_返回entity() {
        // 第一次全名查询返回空, 第二次模糊查询返回记录
        when(mapper.selectList(any()))
                .thenReturn(List.of())
                .thenReturn(List.of(stubEmp(2L, "E002", "张三丰")));
        EmployeeEntity r = service.findByName("张三");
        assertNotNull(r);
        assertEquals(2L, r.getId());
    }

    @Test
    void findByName_空字符串_返回null() {
        assertNull(service.findByName(null));
        assertNull(service.findByName(""));
        verify(mapper, never()).selectList(any());
    }

    @Test
    void findByName_完全无匹配_返回null() {
        when(mapper.selectList(any())).thenReturn(List.of());
        assertNull(service.findByName("不存在的人"));
    }

    @Test
    void create_姓名空_throw() {
        EmployeeEntity e = stubEmp(null, "E001", "");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(e));
        assertTrue(ex.getMessage().contains("姓名不能为空"));
    }

    @Test
    void create_工号重复_throw() {
        EmployeeEntity e = stubEmp(null, "E001", "员工A");
        when(mapper.selectCount(any())).thenReturn(1L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(e));
        assertTrue(ex.getMessage().contains("工号已存在"));
    }

    @Test
    void create_默认isActive_true() {
        EmployeeEntity e = stubEmp(null, null, "员工A");
        e.setIsActive(null);
        // code=null 时 validateCode 跳过, 不调 selectCount
        lenient().when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(EmployeeEntity.class))).thenReturn(1);
        EmployeeEntity r = service.create(e);
        assertTrue(r.getIsActive());
        verify(mapper).insert(any(EmployeeEntity.class));
    }
}
