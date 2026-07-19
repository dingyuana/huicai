package com.huicai.sme.cash.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.sme.cash.entity.TicketEntity;
import com.huicai.sme.cash.entity.TicketTransactionEntity;
import com.huicai.sme.cash.mapper.TicketMapper;
import com.huicai.sme.cash.mapper.TicketTransactionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplTest {

    @Mock private TicketMapper mapper;
    @Mock private TicketTransactionMapper transactionMapper;
    private TicketServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new TicketServiceImpl(mapper, transactionMapper);
        Field baseMapper = com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class
                .getDeclaredField("baseMapper");
        baseMapper.setAccessible(true);
        baseMapper.set(service, mapper);
    }

    private TicketEntity stubEntity() {
        TicketEntity e = new TicketEntity();
        e.setId(1L);
        e.setTicketNo("TP-202607-001");
        e.setTicketType("BANK_ACCEPTANCE");
        e.setAmount(BigDecimal.valueOf(50000));
        e.setBankId(1L);
        e.setPayee("测试收款人");
        e.setIssueDate(LocalDate.now());
        e.setExpireDate(LocalDate.now().plusMonths(6));
        e.setStatus("IN_STOCK");
        e.setCreatedBy(1L);
        e.setDeleted(0);
        return e;
    }

    @Test
    void getById_存在_返回Entity() {
        when(mapper.selectById(1L)).thenReturn(stubEntity());
        TicketEntity result = service.getById(1L);
        assertNotNull(result);
    }

    @Test
    void getById_不存在_抛BusinessException() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getById(99L));
    }

    @Test
    void create_正常_调insert() {
        TicketEntity e = stubEntity();
        e.setAmount(BigDecimal.valueOf(1000));
        service.create(e, 1L);
        verify(mapper).insert(any(TicketEntity.class));
    }

    @Test
    void create_金额为零_抛BusinessException() {
        TicketEntity e = stubEntity();
        e.setAmount(BigDecimal.ZERO);
        assertThrows(BusinessException.class, () -> service.create(e, 1L));
    }

    @Test
    void delete_调deleteById() {
        when(mapper.selectById(1L)).thenReturn(stubEntity());
        service.delete(1L);
        verify(mapper).deleteById(1L);
    }

    @Test
    void getTransactions_调selectList() {
        when(transactionMapper.selectList(any())).thenReturn(Collections.singletonList(new TicketTransactionEntity()));
        service.getTransactions(1L);
        verify(transactionMapper).selectList(any());
    }
}