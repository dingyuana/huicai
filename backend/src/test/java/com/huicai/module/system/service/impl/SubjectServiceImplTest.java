package com.huicai.base.system.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubjectServiceImplTest {

    @Mock private SubjectMapper subjectMapper;
    @InjectMocks private SubjectServiceImpl service;

    private Subject stubEntity() {
        Subject e = new Subject();
        e.setId(1L);
        e.setCode("1001");
        e.setName("现金");
        e.setDirection("DEBIT");
        e.setIsActive(true);
        e.setLevel(1);
        return e;
    }

    @Test
    void getById_存在_返回Entity() {
        when(subjectMapper.selectById(1L)).thenReturn(stubEntity());
        Subject result = service.getById(1L);
        assertNotNull(result);
        verify(subjectMapper).selectById(1L);
    }

    @Test
    void getById_不存在_抛BusinessException() {
        when(subjectMapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getById(99L));
    }

    @Test
    void getTree_调selectList() {
        when(subjectMapper.selectList(any())).thenReturn(java.util.Collections.singletonList(stubEntity()));
        service.getTree();
        verify(subjectMapper).selectList(any());
    }
}
