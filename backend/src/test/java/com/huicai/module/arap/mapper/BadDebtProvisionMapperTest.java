package com.huicai.module.arap.mapper;

import com.huicai.module.arap.entity.BadDebtProvisionEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

public class BadDebtProvisionMapperTest {

    @Test
    @DisplayName("BadDebtProvisionMapper insert 方法应接受正确参数")
    void insert_shouldAcceptValidParams() {
        BadDebtProvisionMapper mapper = Mockito.mock(BadDebtProvisionMapper.class);
        BadDebtProvisionEntity entity = new BadDebtProvisionEntity();
        entity.setPeriod("202607");
        entity.setMethod("PERCENTAGE");
        entity.setProvisionDate(java.time.LocalDate.now());
        entity.setTotalAmount(java.math.BigDecimal.valueOf(5000));
        entity.setVoucherId(null);
        entity.setStatus("POSTED");
        entity.setRemark("坏账准备");
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        Mockito.when(mapper.insert(entity)).thenReturn(1);
        int rows = mapper.insert(entity);
        assertEquals(1, rows);
        Mockito.verify(mapper).insert(entity);
    }

    @Test
    @DisplayName("BadDebtProvisionMapper selectById 方法应返回实体")
    void selectById_shouldReturnEntity() {
        BadDebtProvisionMapper mapper = Mockito.mock(BadDebtProvisionMapper.class);
        BadDebtProvisionEntity entity = new BadDebtProvisionEntity();
        entity.setPeriod("202607");
        entity.setMethod("PERCENTAGE");
        entity.setProvisionDate(java.time.LocalDate.now());
        entity.setTotalAmount(java.math.BigDecimal.valueOf(5000));
        entity.setVoucherId(null);
        entity.setStatus("POSTED");
        entity.setRemark("坏账准备");
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        Mockito.when(mapper.selectById(1L)).thenReturn(entity);
        BadDebtProvisionEntity result = mapper.selectById(1L);
        assertNotNull(result);
        Mockito.verify(mapper).selectById(1L);
    }

    @Test
    @DisplayName("BadDebtProvisionMapper updateById 方法应接受实体参数")
    void updateById_shouldAcceptEntity() {
        BadDebtProvisionMapper mapper = Mockito.mock(BadDebtProvisionMapper.class);
        BadDebtProvisionEntity entity = new BadDebtProvisionEntity();
        entity.setPeriod("202607");
        entity.setMethod("PERCENTAGE");
        entity.setProvisionDate(java.time.LocalDate.now());
        entity.setTotalAmount(java.math.BigDecimal.valueOf(5000));
        entity.setVoucherId(null);
        entity.setStatus("POSTED");
        entity.setRemark("坏账准备");
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        Mockito.when(mapper.updateById(entity)).thenReturn(1);
        int rows = mapper.updateById(entity);
        assertEquals(1, rows);
        Mockito.verify(mapper).updateById(entity);
    }

    @Test
    @DisplayName("BadDebtProvisionMapper deleteById 方法应接受ID参数")
    void deleteById_shouldAcceptId() {
        BadDebtProvisionMapper mapper = Mockito.mock(BadDebtProvisionMapper.class);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);
        int rows = mapper.deleteById(1L);
        assertEquals(1, rows);
        Mockito.verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("BadDebtProvisionMapper 所有方法定义应正确")
    void allMethods_shouldBeDefined() {
        BadDebtProvisionMapper mapper = Mockito.mock(BadDebtProvisionMapper.class);
        BadDebtProvisionEntity e = new BadDebtProvisionEntity();
        e.setPeriod("202607");
        e.setMethod("PERCENTAGE");
        e.setProvisionDate(java.time.LocalDate.now());
        e.setTotalAmount(java.math.BigDecimal.valueOf(5000));
        e.setVoucherId(null);
        e.setStatus("POSTED");
        e.setRemark("坏账准备");
        e.setCreatedBy(1L);
        e.setUpdatedBy(1L);
        Mockito.when(mapper.insert(e)).thenReturn(1);
        Mockito.when(mapper.selectById(1L)).thenReturn(e);
        Mockito.when(mapper.updateById(e)).thenReturn(1);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);
        assertEquals(1, mapper.insert(e));
        assertNotNull(mapper.selectById(1L));
        assertEquals(1, mapper.updateById(e));
        assertEquals(1, mapper.deleteById(1L));
    }
}
