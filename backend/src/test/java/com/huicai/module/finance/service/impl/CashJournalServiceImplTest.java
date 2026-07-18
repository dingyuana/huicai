package com.huicai.module.finance.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.finance.entity.CashJournalEntity;
import com.huicai.module.finance.mapper.CashJournalMapper;
import com.huicai.module.finance.service.VoucherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CashJournalServiceImplTest {

    @Mock private CashJournalMapper mapper;
    @Mock private VoucherService voucherService;
    private CashJournalServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new CashJournalServiceImpl(mapper, voucherService);
        Field baseMapper = com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class
                .getDeclaredField("baseMapper");
        baseMapper.setAccessible(true);
        baseMapper.set(service, mapper);
    }

    private CashJournalEntity stubEntity() {
        CashJournalEntity e = new CashJournalEntity();
        e.setId(1L);
        e.setPeriod("202607");
        e.setJournalDate(LocalDate.now());
        e.setJournalNo("XJ-202607-001");
        e.setSummary("测试现金日记账");
        e.setDebit(java.math.BigDecimal.valueOf(1000));
        e.setCredit(java.math.BigDecimal.valueOf(0));
        e.setBalance(java.math.BigDecimal.valueOf(1000));
        e.setSubjectId(1L);
        e.setDeleted(0);
        return e;
    }

    @Test
    void getById_存在_返回Entity() {
        when(mapper.selectById(1L)).thenReturn(stubEntity());
        CashJournalEntity result = service.getById(1L);
        assertNotNull(result);
    }

    @Test
    void getById_不存在_抛BusinessException() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getById(99L));
    }

    @Test
    void pageQuery_调selectPage() {
        when(mapper.selectPage(any(), any())).thenReturn(null);
        service.pageQuery("202607", null, null, null, null);
        verify(mapper).selectPage(any(), any());
    }
}