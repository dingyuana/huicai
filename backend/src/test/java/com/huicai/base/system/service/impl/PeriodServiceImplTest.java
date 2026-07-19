package com.huicai.base.system.service.impl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.mapper.PeriodMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PeriodServiceImplTest {

    @Mock private PeriodMapper periodMapper;
    @Spy private PeriodServiceImpl service = new PeriodServiceImpl();

    @BeforeEach
    void setUp() {
        // MyBatis-Plus ServiceImpl requires baseMapper injection via ReflectionTestUtils
        ReflectionTestUtils.setField(service, "baseMapper", periodMapper);
    }

    private PeriodEntity stubEntity() {
        PeriodEntity e = new PeriodEntity();
        e.setId(1L);
        e.setYear(2026);
        e.setMonth(7);
        e.setPeriodCode("202607");
        e.setStartDate(java.time.LocalDate.of(2026, 7, 1));
        e.setEndDate(java.time.LocalDate.of(2026, 7, 31));
        e.setStatus("OPEN");
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
