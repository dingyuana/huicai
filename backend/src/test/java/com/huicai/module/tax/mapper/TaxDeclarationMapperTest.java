package com.huicai.module.tax.mapper;

import com.huicai.module.tax.entity.TaxDeclarationEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

public class TaxDeclarationMapperTest {

    @Test
    @DisplayName("TaxDeclarationMapper insert 方法应接受正确参数")
    void insert_shouldAcceptValidParams() {
        TaxDeclarationMapper mapper = Mockito.mock(TaxDeclarationMapper.class);
        TaxDeclarationEntity entity = new TaxDeclarationEntity();
        entity.setDeclarationNo("DECL-001");
        entity.setPeriod("202607");
        entity.setTaxType("VAT");
        entity.setPayableAmount(java.math.BigDecimal.valueOf(5000));
        entity.setStatus("DRAFT");
        entity.setVoucherId(null);
        entity.setRemark("增值税申报");
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        Mockito.when(mapper.insert(entity)).thenReturn(1);
        int rows = mapper.insert(entity);
        assertEquals(1, rows);
        Mockito.verify(mapper).insert(entity);
    }

    @Test
    @DisplayName("TaxDeclarationMapper selectById 方法应返回实体")
    void selectById_shouldReturnEntity() {
        TaxDeclarationMapper mapper = Mockito.mock(TaxDeclarationMapper.class);
        TaxDeclarationEntity entity = new TaxDeclarationEntity();
        entity.setDeclarationNo("DECL-001");
        entity.setPeriod("202607");
        entity.setTaxType("VAT");
        entity.setPayableAmount(java.math.BigDecimal.valueOf(5000));
        entity.setStatus("DRAFT");
        entity.setVoucherId(null);
        entity.setRemark("增值税申报");
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        Mockito.when(mapper.selectById(1L)).thenReturn(entity);
        TaxDeclarationEntity result = mapper.selectById(1L);
        assertNotNull(result);
        Mockito.verify(mapper).selectById(1L);
    }

    @Test
    @DisplayName("TaxDeclarationMapper updateById 方法应接受实体参数")
    void updateById_shouldAcceptEntity() {
        TaxDeclarationMapper mapper = Mockito.mock(TaxDeclarationMapper.class);
        TaxDeclarationEntity entity = new TaxDeclarationEntity();
        entity.setDeclarationNo("DECL-001");
        entity.setPeriod("202607");
        entity.setTaxType("VAT");
        entity.setPayableAmount(java.math.BigDecimal.valueOf(5000));
        entity.setStatus("DRAFT");
        entity.setVoucherId(null);
        entity.setRemark("增值税申报");
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        Mockito.when(mapper.updateById(entity)).thenReturn(1);
        int rows = mapper.updateById(entity);
        assertEquals(1, rows);
        Mockito.verify(mapper).updateById(entity);
    }

    @Test
    @DisplayName("TaxDeclarationMapper deleteById 方法应接受ID参数")
    void deleteById_shouldAcceptId() {
        TaxDeclarationMapper mapper = Mockito.mock(TaxDeclarationMapper.class);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);
        int rows = mapper.deleteById(1L);
        assertEquals(1, rows);
        Mockito.verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("TaxDeclarationMapper 所有方法定义应正确")
    void allMethods_shouldBeDefined() {
        TaxDeclarationMapper mapper = Mockito.mock(TaxDeclarationMapper.class);
        TaxDeclarationEntity e = new TaxDeclarationEntity();
        e.setDeclarationNo("DECL-001");
        e.setPeriod("202607");
        e.setTaxType("VAT");
        e.setPayableAmount(java.math.BigDecimal.valueOf(5000));
        e.setStatus("DRAFT");
        e.setVoucherId(null);
        e.setRemark("增值税申报");
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
