package com.huicai.base.system.service.impl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.mapper.PeriodMapper;
import com.huicai.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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

    @Test
    void save_存在软删残留_物理清理后允许重建() {
        // 软删记录占位唯一索引时, save 应物理清理软删记录并允许插入
        when(periodMapper.purgeSoftDeleted("202606", null)).thenReturn(1);
        when(periodMapper.selectCount(any())).thenReturn(0L);

        PeriodEntity e = new PeriodEntity();
        e.setPeriodCode("202606");
        service.save(e);

        verify(periodMapper).purgeSoftDeleted("202606", null);
        verify(periodMapper).insert(any(PeriodEntity.class));
    }

    @Test
    void save_存在活跃记录_拒绝重复创建() {
        // 活跃记录(未删除)存在时, 应抛冲突
        when(periodMapper.purgeSoftDeleted("202606", null)).thenReturn(0);
        when(periodMapper.selectCount(any())).thenReturn(1L);

        PeriodEntity e = new PeriodEntity();
        e.setPeriodCode("202606");

        assertThrows(BusinessException.class, () -> service.save(e));
        verify(periodMapper, never()).insert(any(PeriodEntity.class));
    }
}
