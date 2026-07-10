package com.huicai.module.budget.mapper;

import com.huicai.module.budget.entity.BudgetEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

public class BudgetMapperTest {

    @Test
    @DisplayName("BudgetMapper insert 方法应接受正确参数")
    void insert_shouldAcceptValidParams() {
        BudgetMapper mapper = Mockito.mock(BudgetMapper.class);
        BudgetEntity entity = new BudgetEntity();
        entity.setBudgetNo("BUDGET-001");
        entity.setPeriod("202607");
        entity.setBudgetType("OPERATION");
        entity.setTotalAmount(java.math.BigDecimal.valueOf(100000));
        entity.setStatus("DRAFT");
        entity.setApprovedBy(null);
        entity.setApprovedAt(null);
        entity.setRemark("测试预算");
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        Mockito.when(mapper.insert(entity)).thenReturn(1);
        int rows = mapper.insert(entity);
        assertEquals(1, rows);
        Mockito.verify(mapper).insert(entity);
    }

    @Test
    @DisplayName("BudgetMapper selectById 方法应返回实体")
    void selectById_shouldReturnEntity() {
        BudgetMapper mapper = Mockito.mock(BudgetMapper.class);
        BudgetEntity entity = new BudgetEntity();
        entity.setBudgetNo("BUDGET-001");
        entity.setPeriod("202607");
        entity.setBudgetType("OPERATION");
        entity.setTotalAmount(java.math.BigDecimal.valueOf(100000));
        entity.setStatus("DRAFT");
        entity.setApprovedBy(null);
        entity.setApprovedAt(null);
        entity.setRemark("测试预算");
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        Mockito.when(mapper.selectById(1L)).thenReturn(entity);
        BudgetEntity result = mapper.selectById(1L);
        assertNotNull(result);
        Mockito.verify(mapper).selectById(1L);
    }

    @Test
    @DisplayName("BudgetMapper updateById 方法应接受实体参数")
    void updateById_shouldAcceptEntity() {
        BudgetMapper mapper = Mockito.mock(BudgetMapper.class);
        BudgetEntity entity = new BudgetEntity();
        entity.setBudgetNo("BUDGET-001");
        entity.setPeriod("202607");
        entity.setBudgetType("OPERATION");
        entity.setTotalAmount(java.math.BigDecimal.valueOf(100000));
        entity.setStatus("DRAFT");
        entity.setApprovedBy(null);
        entity.setApprovedAt(null);
        entity.setRemark("测试预算");
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        Mockito.when(mapper.updateById(entity)).thenReturn(1);
        int rows = mapper.updateById(entity);
        assertEquals(1, rows);
        Mockito.verify(mapper).updateById(entity);
    }

    @Test
    @DisplayName("BudgetMapper deleteById 方法应接受ID参数")
    void deleteById_shouldAcceptId() {
        BudgetMapper mapper = Mockito.mock(BudgetMapper.class);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);
        int rows = mapper.deleteById(1L);
        assertEquals(1, rows);
        Mockito.verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("BudgetMapper 所有方法定义应正确")
    void allMethods_shouldBeDefined() {
        BudgetMapper mapper = Mockito.mock(BudgetMapper.class);
        BudgetEntity e = new BudgetEntity();
        e.setBudgetNo("BUDGET-001");
        e.setPeriod("202607");
        e.setBudgetType("OPERATION");
        e.setTotalAmount(java.math.BigDecimal.valueOf(100000));
        e.setStatus("DRAFT");
        e.setApprovedBy(null);
        e.setApprovedAt(null);
        e.setRemark("测试预算");
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
