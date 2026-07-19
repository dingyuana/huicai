package com.huicai.base.system.service.impl;

import com.huicai.base.system.entity.AuditLogEntity;
import com.huicai.base.system.mapper.AuditLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceImplTest {

    @Mock private AuditLogMapper mapper;
    @InjectMocks private AuditLogServiceImpl service;

    private AuditLogEntity stubEntity() {
        AuditLogEntity e = new AuditLogEntity();
        e.setId(1L);
        e.setUserId(1L);
        e.setUsername("admin");
        e.setOperation("LOGIN");
        e.setMethod("POST");
        return e;
    }

    @Test
    void getById_存在_返回Entity() {
        when(mapper.selectById(1L)).thenReturn(stubEntity());
        AuditLogEntity result = service.getById(1L);
        assertNotNull(result);
    }

    @Test
    void getById_不存在_返回Null() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertNull(service.getById(99L));
    }

    @Test
    void pageLog_调selectPage() {
        when(mapper.selectPage(any(), any())).thenReturn(null);
        service.pageLog(1, 20, null, null, null, null);
        verify(mapper).selectPage(any(), any());
    }
}