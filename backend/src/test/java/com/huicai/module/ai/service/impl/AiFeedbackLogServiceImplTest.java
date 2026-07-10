package com.huicai.module.ai.service.impl;

import com.huicai.module.ai.entity.AiFeedbackLogEntity;
import com.huicai.module.ai.mapper.AiFeedbackLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiFeedbackLogServiceImplTest {

    @Mock private AiFeedbackLogMapper mapper;
    @InjectMocks private AiFeedbackLogServiceImpl service;

    private AiFeedbackLogEntity stubEntity() {
        AiFeedbackLogEntity e = new AiFeedbackLogEntity();
        e.setId(1L);
        e.setTenantId(1L);
        e.setBankTxnId(1L);
        e.setAiSuggestedAction("CLASSIFY");
        e.setAiConfidence(85);
        e.setAiBusinessScene("报销");
        e.setHumanAction("CONFIRM");
        e.setCreatedBy(1L);
        return e;
    }

    @Test
    void getById_存在_返回Entity() {
        when(mapper.selectById(1L)).thenReturn(stubEntity());
        AiFeedbackLogEntity result = service.getById(1L);
        assertNotNull(result);
    }

    @Test
    void getById_不存在_返回Null() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertNull(service.getById(99L));
    }

    @Test
    void deleteByBankTxn_调delete() {
        service.deleteByBankTxn(1L);
        verify(mapper).delete(any());
    }

    @Test
    void page_调selectPage() {
        when(mapper.selectPage(any(), any())).thenReturn(null);
        service.page(null, null, null, null, null);
        verify(mapper).selectPage(any(), any());
    }
}