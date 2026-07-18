package com.huicai.module.system.service.impl;

import com.huicai.module.system.entity.PeriodEntity;
import com.huicai.module.system.mapper.PeriodMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PeriodServiceImplTest {

    @Mock private PeriodMapper periodMapper;
    private PeriodServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new PeriodServiceImpl();
        // MyBatis-Plus ServiceImpl stores mapper in baseMapper field
        Field baseMapper = com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class
                .getDeclaredField("baseMapper");
        baseMapper.setAccessible(true);
        baseMapper.set(service, periodMapper);
    }

    private PeriodEntity stubEntity() {
        PeriodEntity e = new PeriodEntity();
        e.setId(1L);
        e.setYear(2026);
        e.setMonth(7);
        e.setPeriodCode("202607");
        e.setStartDate(LocalDate.of(2026, 7, 1));
        e.setEndDate(LocalDate.of(2026, 7, 31));
        e.setStatus("open");
        return e;
    }

    @Test
    void save_正常_调insert() {
        service.save(stubEntity());
        verify(periodMapper).insert(any(PeriodEntity.class));
    }

    @Test
    void openPeriod_正常_调updateById() {
        when(periodMapper.selectById(1L)).thenReturn(stubEntity());
        service.openPeriod(1L);
        verify(periodMapper).updateById(any(PeriodEntity.class));
    }

    @Test
    void closePeriod_正常_调updateById() {
        when(periodMapper.selectById(1L)).thenReturn(stubEntity());
        service.closePeriod(1L);
        verify(periodMapper).updateById(any(PeriodEntity.class));
    }
}
