package com.huicai.base.system.service.impl;

import com.huicai.base.system.entity.SysConfigEntity;
import com.huicai.base.system.mapper.SysConfigMapper;
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
class SysConfigServiceImplTest {

    @Mock private SysConfigMapper mapper;
    private SysConfigServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new SysConfigServiceImpl();
        Field baseMapper = com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class
                .getDeclaredField("baseMapper");
        baseMapper.setAccessible(true);
        baseMapper.set(service, mapper);
    }

    private SysConfigEntity stubEntity() {
        SysConfigEntity e = new SysConfigEntity();
        e.setId(1L);
        e.setConfigKey("company.name");
        e.setConfigValue("测试公司");
        e.setConfigType("TEXT");
        e.setIsActive(true);
        return e;
    }

    @Test
    void save_正常_调insert() {
        service.save(stubEntity());
        verify(mapper).insert(any(SysConfigEntity.class));
    }

    @Test
    void getById_存在_返回Entity() {
        when(mapper.selectById(1L)).thenReturn(stubEntity());
        SysConfigEntity result = service.getById(1L);
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
        verify(mapper).updateById(any(SysConfigEntity.class));
    }

    @Test
    void delete_调deleteById() {
        service.removeById(1L);
        verify(mapper).deleteById(1L);
    }
}