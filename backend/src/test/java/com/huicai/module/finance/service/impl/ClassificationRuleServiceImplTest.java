package com.huicai.module.finance.service.impl;

import com.huicai.module.finance.entity.ClassificationRuleEntity;
import com.huicai.module.finance.mapper.ClassificationRuleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassificationRuleServiceImplTest {

    @Mock private ClassificationRuleMapper mapper;
    @InjectMocks private ClassificationRuleServiceImpl service;

    private ClassificationRuleEntity stubEntity() {
        ClassificationRuleEntity e = new ClassificationRuleEntity();
        e.setId(1L);
        e.setRuleType("keyword_regex");
        e.setMatchField("description");
        e.setPattern("报销");
        e.setClassification("EXPENSE");
        e.setPriority(1);
        e.setIsActive(true);
        return e;
    }

    @Test
    void getById_存在_返回Entity() {
        when(mapper.selectById(1L)).thenReturn(stubEntity());
        ClassificationRuleEntity result = service.getById(1L);
        assertNotNull(result);
    }

    @Test
    void getById_不存在_返回Null() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertNull(service.getById(99L));
    }

    @Test
    void create_正常_调insert() {
        ClassificationRuleEntity e = stubEntity();
        e.setTenantId(1L);
        service.create(e);
        verify(mapper).insert(any(ClassificationRuleEntity.class));
    }

    @Test
    void update_存在_调updateById() {
        when(mapper.selectById(1L)).thenReturn(stubEntity());
        service.update(1L, stubEntity());
        verify(mapper).updateById(any(ClassificationRuleEntity.class));
    }

    @Test
    void update_不存在_返回Null() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertNull(service.update(99L, stubEntity()));
    }

    @Test
    void delete_调deleteById() {
        service.delete(1L);
        verify(mapper).deleteById(1L);
    }

    @Test
    void page_调selectPage() {
        when(mapper.selectPage(any(), any())).thenReturn(null);
        service.page(null, 1, 20);
        verify(mapper).selectPage(any(), any());
    }
}