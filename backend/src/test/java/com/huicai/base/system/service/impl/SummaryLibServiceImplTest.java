package com.huicai.base.system.service.impl;

import com.huicai.base.system.entity.SummaryLibEntity;
import com.huicai.base.system.mapper.SummaryLibMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SummaryLibServiceImplTest {

    @Mock private SummaryLibMapper mapper;
    private SummaryLibServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new SummaryLibServiceImpl();
        Field baseMapper = com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class
                .getDeclaredField("baseMapper");
        baseMapper.setAccessible(true);
        baseMapper.set(service, mapper);
    }

    private SummaryLibEntity stubEntity() {
        SummaryLibEntity e = new SummaryLibEntity();
        e.setId(1L);
        e.setSummaryCode("S001");
        e.setSummaryText("差旅费");
        e.setCategory("EXPENSE");
        e.setSortOrder(1);
        e.setIsActive(true);
        return e;
    }

    @Test
    void save_正常_调insert() {
        service.save(stubEntity());
        verify(mapper).insert(any(SummaryLibEntity.class));
    }

    @Test
    void getById_存在_返回Entity() {
        when(mapper.selectById(1L)).thenReturn(stubEntity());
        SummaryLibEntity result = service.getById(1L);
        assertNotNull(result);
    }

    @Test
    void getById_不存在_返回Null() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertNull(service.getById(99L));
    }

    @Test
    void updateById_调updateById() {
        service.updateById(stubEntity());
        verify(mapper).updateById(any(SummaryLibEntity.class));
    }

    @Test
    void delete_调deleteById() {
        service.removeById(1L);
        verify(mapper).deleteById(1L);
    }
}