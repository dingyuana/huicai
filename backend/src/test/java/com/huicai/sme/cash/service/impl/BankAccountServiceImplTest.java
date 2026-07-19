package com.huicai.sme.cash.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.sme.cash.entity.BankAccountEntity;
import com.huicai.sme.cash.mapper.BankAccountMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankAccountServiceImplTest {

    @Mock private BankAccountMapper mapper;
    @InjectMocks private BankAccountServiceImpl service;

    private BankAccountEntity stubEntity() {
        BankAccountEntity e = new BankAccountEntity();
        e.setId(1L);
        e.setAccountNo("6222000012345678");
        e.setAccountName("测试账户");
        e.setBankName("工商银行");
        e.setIsActive(true);
        return e;
    }

    @Test
    void getById_存在_返回Entity() {
        when(mapper.selectById(1L)).thenReturn(stubEntity());
        BankAccountEntity result = service.getById(1L);
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
        verify(mapper).insert(any(BankAccountEntity.class));
    }

    @Test
    void update_正常_调updateById() {
        when(mapper.selectById(1L)).thenReturn(stubEntity());
        service.update(1L, stubEntity());
        verify(mapper).updateById(any(BankAccountEntity.class));
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

    @Test
    void listActive_调selectList() {
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(stubEntity()));
        service.listActive();
        verify(mapper).selectList(any());
    }
}