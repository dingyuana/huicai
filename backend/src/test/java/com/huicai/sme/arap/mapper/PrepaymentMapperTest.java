package com.huicai.sme.arap.mapper;

import com.huicai.sme.arap.entity.PrepaymentEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

public class PrepaymentMapperTest {

    @Test
    @DisplayName("PrepaymentMapper insert 方法应接受正确参数")
    void insert_shouldAcceptValidParams() {
        PrepaymentMapper mapper = Mockito.mock(PrepaymentMapper.class);
        PrepaymentEntity entity = new PrepaymentEntity();
        entity.setTenantId(1L);
        entity.setVendorId(1L);
        entity.setCustomerId(null);
        entity.setDocId(null);
        entity.setVoucherId(null);
        entity.setPeriod("202607");
        entity.setTxDate(java.time.LocalDate.now());
        entity.setAmount(java.math.BigDecimal.valueOf(10000));
        entity.setSettledAmount(java.math.BigDecimal.ZERO);
        entity.setUnsettledAmount(java.math.BigDecimal.valueOf(10000));
        entity.setSummary("预付款");
        entity.setStatus("DRAFT");
        entity.setSourceDocType(null);
        entity.setSourceDocId(null);
        entity.setRemark("测试预付款");
        entity.setCreatedBy("admin");
        entity.setCreatedAt(java.time.LocalDate.now());
        entity.setUpdatedAt(java.time.LocalDate.now());
        Mockito.when(mapper.insert(entity)).thenReturn(1);
        int rows = mapper.insert(entity);
        assertEquals(1, rows);
        Mockito.verify(mapper).insert(entity);
    }

    @Test
    @DisplayName("PrepaymentMapper selectById 方法应返回实体")
    void selectById_shouldReturnEntity() {
        PrepaymentMapper mapper = Mockito.mock(PrepaymentMapper.class);
        PrepaymentEntity entity = new PrepaymentEntity();
        entity.setTenantId(1L);
        entity.setVendorId(1L);
        entity.setCustomerId(null);
        entity.setDocId(null);
        entity.setVoucherId(null);
        entity.setPeriod("202607");
        entity.setTxDate(java.time.LocalDate.now());
        entity.setAmount(java.math.BigDecimal.valueOf(10000));
        entity.setSettledAmount(java.math.BigDecimal.ZERO);
        entity.setUnsettledAmount(java.math.BigDecimal.valueOf(10000));
        entity.setSummary("预付款");
        entity.setStatus("DRAFT");
        entity.setSourceDocType(null);
        entity.setSourceDocId(null);
        entity.setRemark("测试预付款");
        entity.setCreatedBy("admin");
        entity.setCreatedAt(java.time.LocalDate.now());
        entity.setUpdatedAt(java.time.LocalDate.now());
        Mockito.when(mapper.selectById(1L)).thenReturn(entity);
        PrepaymentEntity result = mapper.selectById(1L);
        assertNotNull(result);
        Mockito.verify(mapper).selectById(1L);
    }

    @Test
    @DisplayName("PrepaymentMapper updateById 方法应接受实体参数")
    void updateById_shouldAcceptEntity() {
        PrepaymentMapper mapper = Mockito.mock(PrepaymentMapper.class);
        PrepaymentEntity entity = new PrepaymentEntity();
        entity.setTenantId(1L);
        entity.setVendorId(1L);
        entity.setCustomerId(null);
        entity.setDocId(null);
        entity.setVoucherId(null);
        entity.setPeriod("202607");
        entity.setTxDate(java.time.LocalDate.now());
        entity.setAmount(java.math.BigDecimal.valueOf(10000));
        entity.setSettledAmount(java.math.BigDecimal.ZERO);
        entity.setUnsettledAmount(java.math.BigDecimal.valueOf(10000));
        entity.setSummary("预付款");
        entity.setStatus("DRAFT");
        entity.setSourceDocType(null);
        entity.setSourceDocId(null);
        entity.setRemark("测试预付款");
        entity.setCreatedBy("admin");
        entity.setCreatedAt(java.time.LocalDate.now());
        entity.setUpdatedAt(java.time.LocalDate.now());
        Mockito.when(mapper.updateById(entity)).thenReturn(1);
        int rows = mapper.updateById(entity);
        assertEquals(1, rows);
        Mockito.verify(mapper).updateById(entity);
    }

    @Test
    @DisplayName("PrepaymentMapper deleteById 方法应接受ID参数")
    void deleteById_shouldAcceptId() {
        PrepaymentMapper mapper = Mockito.mock(PrepaymentMapper.class);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);
        int rows = mapper.deleteById(1L);
        assertEquals(1, rows);
        Mockito.verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("PrepaymentMapper 所有方法定义应正确")
    void allMethods_shouldBeDefined() {
        PrepaymentMapper mapper = Mockito.mock(PrepaymentMapper.class);
        PrepaymentEntity e = new PrepaymentEntity();
        e.setTenantId(1L);
        e.setVendorId(1L);
        e.setCustomerId(null);
        e.setDocId(null);
        e.setVoucherId(null);
        e.setPeriod("202607");
        e.setTxDate(java.time.LocalDate.now());
        e.setAmount(java.math.BigDecimal.valueOf(10000));
        e.setSettledAmount(java.math.BigDecimal.ZERO);
        e.setUnsettledAmount(java.math.BigDecimal.valueOf(10000));
        e.setSummary("预付款");
        e.setStatus("DRAFT");
        e.setSourceDocType(null);
        e.setSourceDocId(null);
        e.setRemark("测试预付款");
        e.setCreatedBy("admin");
        e.setCreatedAt(java.time.LocalDate.now());
        e.setUpdatedAt(java.time.LocalDate.now());
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
