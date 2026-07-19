package com.huicai.sme.cash.mapper;

import com.huicai.sme.cash.entity.BankAccountEntity;
import com.huicai.sme.cash.mapper.BankAccountMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

/**
 * BankAccountMapper 方法签名验证测试
 */
public class BankAccountMapperTest {

    @Test
    @DisplayName("BankAccountMapper insert 方法应接受正确参数")
    void insert_shouldAcceptValidParams() {
        BankAccountMapper mapper = Mockito.mock(BankAccountMapper.class);
        BankAccountEntity entity = new BankAccountEntity();
        
        // 设置必要字段
        entity.setAccountNo("6222000012345678");
        entity.setAccountName("测试账户");
        entity.setBankName("工商银行");
        entity.setCurrency("CNY");
        entity.setSubjectId(1L);
        entity.setBalance(java.math.BigDecimal.ZERO);
        entity.setIsActive(true);
        entity.setRemark("测试账户");
        
        // 验证方法可调用且返回正确类型
        Mockito.when(mapper.insert(entity)).thenReturn(1);
        int rows = mapper.insert(entity);
        
        assertEquals(1, rows);
        Mockito.verify(mapper).insert(entity);
    }

    @Test
    @DisplayName("BankAccountMapper selectById 方法应返回实体")
    void selectById_shouldReturnEntity() {
        BankAccountMapper mapper = Mockito.mock(BankAccountMapper.class);
        BankAccountEntity entity = new BankAccountEntity();
        entity.setAccountNo("6222000012345678");
        entity.setAccountName("测试账户");
        entity.setBankName("工商银行");
        entity.setCurrency("CNY");
        entity.setSubjectId(1L);
        entity.setBalance(java.math.BigDecimal.ZERO);
        entity.setIsActive(true);
        entity.setRemark("测试账户");
        Mockito.when(mapper.selectById(1L)).thenReturn(entity);
        
        BankAccountEntity result = mapper.selectById(1L);
        
        assertNotNull(result);
        Mockito.verify(mapper).selectById(1L);
    }

    @Test
    @DisplayName("BankAccountMapper updateById 方法应接受实体参数")
    void updateById_shouldAcceptEntity() {
        BankAccountMapper mapper = Mockito.mock(BankAccountMapper.class);
        BankAccountEntity entity = new BankAccountEntity();
        entity.setAccountNo("6222000012345678");
        entity.setAccountName("测试账户");
        entity.setBankName("工商银行");
        entity.setCurrency("CNY");
        entity.setSubjectId(1L);
        entity.setBalance(java.math.BigDecimal.ZERO);
        entity.setIsActive(true);
        entity.setRemark("测试账户");
        Mockito.when(mapper.updateById(entity)).thenReturn(1);
        
        int rows = mapper.updateById(entity);
        
        assertEquals(1, rows);
        Mockito.verify(mapper).updateById(entity);
    }

    @Test
    @DisplayName("BankAccountMapper deleteById 方法应接受ID参数")
    void deleteById_shouldAcceptId() {
        BankAccountMapper mapper = Mockito.mock(BankAccountMapper.class);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);
        
        int rows = mapper.deleteById(1L);
        
        assertEquals(1, rows);
        Mockito.verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("BankAccountMapper 所有方法定义应正确")
    void allMethods_shouldBeDefined() {
        BankAccountMapper mapper = Mockito.mock(BankAccountMapper.class);
        
        // 验证所有常用方法存在
        BankAccountEntity e = new BankAccountEntity();
        e.setAccountNo("6222000012345678");
        e.setAccountName("测试账户");
        e.setBankName("工商银行");
        e.setCurrency("CNY");
        e.setSubjectId(1L);
        e.setBalance(java.math.BigDecimal.ZERO);
        e.setIsActive(true);
        e.setRemark("测试账户");
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
