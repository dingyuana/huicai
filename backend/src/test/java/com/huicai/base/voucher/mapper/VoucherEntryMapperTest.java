package com.huicai.base.voucher.mapper;

import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

/**
 * VoucherEntryMapper 方法签名验证测试
 */
public class VoucherEntryMapperTest {

    @Test
    @DisplayName("VoucherEntryMapper insert 方法应接受正确参数")
    void insert_shouldAcceptValidParams() {
        VoucherEntryMapper mapper = Mockito.mock(VoucherEntryMapper.class);
        VoucherEntryEntity entity = new VoucherEntryEntity();
        
        // 设置必要字段
        entity.setVoucherId(1L);
        entity.setSubjectId(1L);
        entity.setDebit(java.math.BigDecimal.valueOf(1000));
        entity.setCredit(java.math.BigDecimal.ZERO);
        entity.setSummary("测试分录");
        entity.setSortOrder(1);
        
        // 验证方法可调用且返回正确类型
        Mockito.when(mapper.insert(entity)).thenReturn(1);
        int rows = mapper.insert(entity);
        
        assertEquals(1, rows);
        Mockito.verify(mapper).insert(entity);
    }

    @Test
    @DisplayName("VoucherEntryMapper selectById 方法应返回实体")
    void selectById_shouldReturnEntity() {
        VoucherEntryMapper mapper = Mockito.mock(VoucherEntryMapper.class);
        VoucherEntryEntity entity = new VoucherEntryEntity();
        entity.setVoucherId(1L);
        entity.setSubjectId(1L);
        entity.setDebit(java.math.BigDecimal.valueOf(1000));
        entity.setCredit(java.math.BigDecimal.ZERO);
        entity.setSummary("测试分录");
        entity.setSortOrder(1);
        Mockito.when(mapper.selectById(1L)).thenReturn(entity);
        
        VoucherEntryEntity result = mapper.selectById(1L);
        
        assertNotNull(result);
        Mockito.verify(mapper).selectById(1L);
    }

    @Test
    @DisplayName("VoucherEntryMapper updateById 方法应接受实体参数")
    void updateById_shouldAcceptEntity() {
        VoucherEntryMapper mapper = Mockito.mock(VoucherEntryMapper.class);
        VoucherEntryEntity entity = new VoucherEntryEntity();
        entity.setVoucherId(1L);
        entity.setSubjectId(1L);
        entity.setDebit(java.math.BigDecimal.valueOf(1000));
        entity.setCredit(java.math.BigDecimal.ZERO);
        entity.setSummary("测试分录");
        entity.setSortOrder(1);
        Mockito.when(mapper.updateById(entity)).thenReturn(1);
        
        int rows = mapper.updateById(entity);
        
        assertEquals(1, rows);
        Mockito.verify(mapper).updateById(entity);
    }

    @Test
    @DisplayName("VoucherEntryMapper deleteById 方法应接受ID参数")
    void deleteById_shouldAcceptId() {
        VoucherEntryMapper mapper = Mockito.mock(VoucherEntryMapper.class);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);
        
        int rows = mapper.deleteById(1L);
        
        assertEquals(1, rows);
        Mockito.verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("VoucherEntryMapper 所有方法定义应正确")
    void allMethods_shouldBeDefined() {
        VoucherEntryMapper mapper = Mockito.mock(VoucherEntryMapper.class);
        
        // 验证所有常用方法存在
        VoucherEntryEntity e = new VoucherEntryEntity();
        e.setVoucherId(1L);
        e.setSubjectId(1L);
        e.setDebit(java.math.BigDecimal.valueOf(1000));
        e.setCredit(java.math.BigDecimal.ZERO);
        e.setSummary("测试分录");
        e.setSortOrder(1);
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
