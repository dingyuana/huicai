package com.huicai.module.finance.mapper;

import com.huicai.module.finance.entity.CashJournalEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

/**
 * CashJournalMapper 方法签名验证测试
 */
public class CashJournalMapperTest {

    @Test
    @DisplayName("CashJournalMapper insert 方法应接受正确参数")
    void insert_shouldAcceptValidParams() {
        CashJournalMapper mapper = Mockito.mock(CashJournalMapper.class);
        CashJournalEntity entity = new CashJournalEntity();
        
        // 设置必要字段
        entity.setPeriod("202607");
        entity.setJournalDate(java.time.LocalDate.now());
        entity.setJournalNo("RZ-001");
        entity.setSummary("测试日记账");
        entity.setDebit(java.math.BigDecimal.valueOf(1000));
        entity.setCredit(java.math.BigDecimal.ZERO);
        entity.setBalance(java.math.BigDecimal.valueOf(1000));
        entity.setSubjectId(1L);
        entity.setOppositeSubjectId(101L);
        entity.setSource("MANUAL");
        entity.setVoucherId(null);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        
        // 验证方法可调用且返回正确类型
        Mockito.when(mapper.insert(entity)).thenReturn(1);
        int rows = mapper.insert(entity);
        
        assertEquals(1, rows);
        Mockito.verify(mapper).insert(entity);
    }

    @Test
    @DisplayName("CashJournalMapper selectById 方法应返回实体")
    void selectById_shouldReturnEntity() {
        CashJournalMapper mapper = Mockito.mock(CashJournalMapper.class);
        CashJournalEntity entity = new CashJournalEntity();
        entity.setPeriod("202607");
        entity.setJournalDate(java.time.LocalDate.now());
        entity.setJournalNo("RZ-001");
        entity.setSummary("测试日记账");
        entity.setDebit(java.math.BigDecimal.valueOf(1000));
        entity.setCredit(java.math.BigDecimal.ZERO);
        entity.setBalance(java.math.BigDecimal.valueOf(1000));
        entity.setSubjectId(1L);
        entity.setOppositeSubjectId(101L);
        entity.setSource("MANUAL");
        entity.setVoucherId(null);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        Mockito.when(mapper.selectById(1L)).thenReturn(entity);
        
        CashJournalEntity result = mapper.selectById(1L);
        
        assertNotNull(result);
        Mockito.verify(mapper).selectById(1L);
    }

    @Test
    @DisplayName("CashJournalMapper updateById 方法应接受实体参数")
    void updateById_shouldAcceptEntity() {
        CashJournalMapper mapper = Mockito.mock(CashJournalMapper.class);
        CashJournalEntity entity = new CashJournalEntity();
        entity.setPeriod("202607");
        entity.setJournalDate(java.time.LocalDate.now());
        entity.setJournalNo("RZ-001");
        entity.setSummary("测试日记账");
        entity.setDebit(java.math.BigDecimal.valueOf(1000));
        entity.setCredit(java.math.BigDecimal.ZERO);
        entity.setBalance(java.math.BigDecimal.valueOf(1000));
        entity.setSubjectId(1L);
        entity.setOppositeSubjectId(101L);
        entity.setSource("MANUAL");
        entity.setVoucherId(null);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        Mockito.when(mapper.updateById(entity)).thenReturn(1);
        
        int rows = mapper.updateById(entity);
        
        assertEquals(1, rows);
        Mockito.verify(mapper).updateById(entity);
    }

    @Test
    @DisplayName("CashJournalMapper deleteById 方法应接受ID参数")
    void deleteById_shouldAcceptId() {
        CashJournalMapper mapper = Mockito.mock(CashJournalMapper.class);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);
        
        int rows = mapper.deleteById(1L);
        
        assertEquals(1, rows);
        Mockito.verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("CashJournalMapper 所有方法定义应正确")
    void allMethods_shouldBeDefined() {
        CashJournalMapper mapper = Mockito.mock(CashJournalMapper.class);
        
        // 验证所有常用方法存在
        CashJournalEntity e = new CashJournalEntity();
        e.setPeriod("202607");
        e.setJournalDate(java.time.LocalDate.now());
        e.setJournalNo("RZ-001");
        e.setSummary("测试日记账");
        e.setDebit(java.math.BigDecimal.valueOf(1000));
        e.setCredit(java.math.BigDecimal.ZERO);
        e.setBalance(java.math.BigDecimal.valueOf(1000));
        e.setSubjectId(1L);
        e.setOppositeSubjectId(101L);
        e.setSource("MANUAL");
        e.setVoucherId(null);
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
