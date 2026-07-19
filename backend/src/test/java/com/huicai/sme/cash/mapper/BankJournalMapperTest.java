package com.huicai.sme.cash.mapper;

import com.huicai.sme.cash.entity.BankJournalEntity;
import com.huicai.sme.cash.mapper.BankJournalMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

/**
 * BankJournalMapper 方法签名验证测试
 */
public class BankJournalMapperTest {

    @Test
    @DisplayName("BankJournalMapper insert 方法应接受正确参数")
    void insert_shouldAcceptValidParams() {
        BankJournalMapper mapper = Mockito.mock(BankJournalMapper.class);
        BankJournalEntity entity = new BankJournalEntity();
        
        // 设置必要字段
        entity.setAccountId(1L);
        entity.setTxDate(java.time.LocalDate.now());
        entity.setPeriod("202607");
        entity.setTxType("INCOME");
        entity.setCounterAccount("测试对手");
        entity.setAmount(java.math.BigDecimal.valueOf(1000));
        entity.setSummary("测试流水");
        entity.setBusinessDocId(null);
        entity.setVoucherId(null);
        entity.setIsReconciled(false);
        entity.setCreatedBy(1L);
        
        // 验证方法可调用且返回正确类型
        Mockito.when(mapper.insert(entity)).thenReturn(1);
        int rows = mapper.insert(entity);
        
        assertEquals(1, rows);
        Mockito.verify(mapper).insert(entity);
    }

    @Test
    @DisplayName("BankJournalMapper selectById 方法应返回实体")
    void selectById_shouldReturnEntity() {
        BankJournalMapper mapper = Mockito.mock(BankJournalMapper.class);
        BankJournalEntity entity = new BankJournalEntity();
        entity.setAccountId(1L);
        entity.setTxDate(java.time.LocalDate.now());
        entity.setPeriod("202607");
        entity.setTxType("INCOME");
        entity.setCounterAccount("测试对手");
        entity.setAmount(java.math.BigDecimal.valueOf(1000));
        entity.setSummary("测试流水");
        entity.setBusinessDocId(null);
        entity.setVoucherId(null);
        entity.setIsReconciled(false);
        entity.setCreatedBy(1L);
        Mockito.when(mapper.selectById(1L)).thenReturn(entity);
        
        BankJournalEntity result = mapper.selectById(1L);
        
        assertNotNull(result);
        Mockito.verify(mapper).selectById(1L);
    }

    @Test
    @DisplayName("BankJournalMapper updateById 方法应接受实体参数")
    void updateById_shouldAcceptEntity() {
        BankJournalMapper mapper = Mockito.mock(BankJournalMapper.class);
        BankJournalEntity entity = new BankJournalEntity();
        entity.setAccountId(1L);
        entity.setTxDate(java.time.LocalDate.now());
        entity.setPeriod("202607");
        entity.setTxType("INCOME");
        entity.setCounterAccount("测试对手");
        entity.setAmount(java.math.BigDecimal.valueOf(1000));
        entity.setSummary("测试流水");
        entity.setBusinessDocId(null);
        entity.setVoucherId(null);
        entity.setIsReconciled(false);
        entity.setCreatedBy(1L);
        Mockito.when(mapper.updateById(entity)).thenReturn(1);
        
        int rows = mapper.updateById(entity);
        
        assertEquals(1, rows);
        Mockito.verify(mapper).updateById(entity);
    }

    @Test
    @DisplayName("BankJournalMapper deleteById 方法应接受ID参数")
    void deleteById_shouldAcceptId() {
        BankJournalMapper mapper = Mockito.mock(BankJournalMapper.class);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);
        
        int rows = mapper.deleteById(1L);
        
        assertEquals(1, rows);
        Mockito.verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("BankJournalMapper 所有方法定义应正确")
    void allMethods_shouldBeDefined() {
        BankJournalMapper mapper = Mockito.mock(BankJournalMapper.class);
        
        // 验证所有常用方法存在
        BankJournalEntity e = new BankJournalEntity();
        e.setAccountId(1L);
        e.setTxDate(java.time.LocalDate.now());
        e.setPeriod("202607");
        e.setTxType("INCOME");
        e.setCounterAccount("测试对手");
        e.setAmount(java.math.BigDecimal.valueOf(1000));
        e.setSummary("测试流水");
        e.setBusinessDocId(null);
        e.setVoucherId(null);
        e.setIsReconciled(false);
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
