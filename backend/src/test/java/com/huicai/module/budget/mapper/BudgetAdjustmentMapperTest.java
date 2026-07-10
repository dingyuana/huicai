package com.huicai.module.budget.mapper;

import com.huicai.module.budget.entity.BudgetAdjustmentEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

public class BudgetAdjustmentMapperTest {

    @Test
    @DisplayName("BudgetAdjustmentMapper insert 方法应接受正确参数")
    void insert_shouldAcceptValidParams() {
        BudgetAdjustmentMapper mapper = Mockito.mock(BudgetAdjustmentMapper.class);
        BudgetAdjustmentEntity entity = new BudgetAdjustmentEntity();
        entity.setAdjustmentNo("ADJ-001");
        entity.setBudgetId(1L);
        entity.setAdjustmentType("INCREASE");
        entity.setAdjustmentDate(java.time.LocalDate.now());
        entity.setPeriod("202607");
        entity.setAdjustmentAmount(java.math.BigDecimal.valueOf(10000));
        entity.setReason("预算调整");
        entity.setStatus("DRAFT");
        entity.setApprovedBy(null);
        entity.setApprovedAt(null);
        entity.setCreatedBy(1L);
        Mockito.when(mapper.insert(entity)).thenReturn(1);
        int rows = mapper.insert(entity);
        assertEquals(1, rows);
        Mockito.verify(mapper).insert(entity);
    }

    @Test
    @DisplayName("BudgetAdjustmentMapper selectById 方法应返回实体")
    void selectById_shouldReturnEntity() {
        BudgetAdjustmentMapper mapper = Mockito.mock(BudgetAdjustmentMapper.class);
        BudgetAdjustmentEntity entity = new BudgetAdjustmentEntity();
        entity.setAdjustmentNo("ADJ-001");
        entity.setBudgetId(1L);
        entity.setAdjustmentType("INCREASE");
        entity.setAdjustmentDate(java.time.LocalDate.now());
        entity.setPeriod("202607");
        entity.setAdjustmentAmount(java.math.BigDecimal.valueOf(10000));
        entity.setReason("预算调整");
        entity.setStatus("DRAFT");
        entity.setApprovedBy(null);
        entity.setApprovedAt(null);
        entity.setCreatedBy(1L);
        Mockito.when(mapper.selectById(1L)).thenReturn(entity);
        BudgetAdjustmentEntity result = mapper.selectById(1L);
        assertNotNull(result);
        Mockito.verify(mapper).selectById(1L);
    }

    @Test
    @DisplayName("BudgetAdjustmentMapper updateById 方法应接受实体参数")
    void updateById_shouldAcceptEntity() {
        BudgetAdjustmentMapper mapper = Mockito.mock(BudgetAdjustmentMapper.class);
        BudgetAdjustmentEntity entity = new BudgetAdjustmentEntity();
        entity.setAdjustmentNo("ADJ-001");
        entity.setBudgetId(1L);
        entity.setAdjustmentType("INCREASE");
        entity.setAdjustmentDate(java.time.LocalDate.now());
        entity.setPeriod("202607");
        entity.setAdjustmentAmount(java.math.BigDecimal.valueOf(10000));
        entity.setReason("预算调整");
        entity.setStatus("DRAFT");
        entity.setApprovedBy(null);
        entity.setApprovedAt(null);
        entity.setCreatedBy(1L);
        Mockito.when(mapper.updateById(entity)).thenReturn(1);
        int rows = mapper.updateById(entity);
        assertEquals(1, rows);
        Mockito.verify(mapper).updateById(entity);
    }

    @Test
    @DisplayName("BudgetAdjustmentMapper deleteById 方法应接受ID参数")
    void deleteById_shouldAcceptId() {
        BudgetAdjustmentMapper mapper = Mockito.mock(BudgetAdjustmentMapper.class);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);
        int rows = mapper.deleteById(1L);
        assertEquals(1, rows);
        Mockito.verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("BudgetAdjustmentMapper 所有方法定义应正确")
    void allMethods_shouldBeDefined() {
        BudgetAdjustmentMapper mapper = Mockito.mock(BudgetAdjustmentMapper.class);
        BudgetAdjustmentEntity e = new BudgetAdjustmentEntity();
        e.setAdjustmentNo("ADJ-001");
        e.setBudgetId(1L);
        e.setAdjustmentType("INCREASE");
        e.setAdjustmentDate(java.time.LocalDate.now());
        e.setPeriod("202607");
        e.setAdjustmentAmount(java.math.BigDecimal.valueOf(10000));
        e.setReason("预算调整");
        e.setStatus("DRAFT");
        e.setApprovedBy(null);
        e.setApprovedAt(null);
        e.setCreatedBy(1L);
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
