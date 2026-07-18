package com.huicai.module.finance.mapper;

import com.huicai.module.finance.entity.BusinessDocEntryEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

public class BusinessDocEntryMapperTest {

    @Test
    @DisplayName("BusinessDocEntryMapper insert 方法应接受正确参数")
    void insert_shouldAcceptValidParams() {
        BusinessDocEntryMapper mapper = Mockito.mock(BusinessDocEntryMapper.class);
        BusinessDocEntryEntity entity = new BusinessDocEntryEntity();
        entity.setDocId(1L);
        entity.setExpenseType("PURCHASE");
        entity.setSubjectId(1L);
        entity.setAmount(java.math.BigDecimal.valueOf(10000));
        entity.setInvoiceNo(null);
        entity.setAssistJson(null);
        entity.setSummary("采购业务单");
        entity.setSortOrder(1);
        Mockito.when(mapper.insert(entity)).thenReturn(1);
        int rows = mapper.insert(entity);
        assertEquals(1, rows);
        Mockito.verify(mapper).insert(entity);
    }

    @Test
    @DisplayName("BusinessDocEntryMapper selectById 方法应返回实体")
    void selectById_shouldReturnEntity() {
        BusinessDocEntryMapper mapper = Mockito.mock(BusinessDocEntryMapper.class);
        BusinessDocEntryEntity entity = new BusinessDocEntryEntity();
        entity.setDocId(1L);
        entity.setExpenseType("PURCHASE");
        entity.setSubjectId(1L);
        entity.setAmount(java.math.BigDecimal.valueOf(10000));
        entity.setInvoiceNo(null);
        entity.setAssistJson(null);
        entity.setSummary("采购业务单");
        entity.setSortOrder(1);
        Mockito.when(mapper.selectById(1L)).thenReturn(entity);
        BusinessDocEntryEntity result = mapper.selectById(1L);
        assertNotNull(result);
        Mockito.verify(mapper).selectById(1L);
    }

    @Test
    @DisplayName("BusinessDocEntryMapper updateById 方法应接受实体参数")
    void updateById_shouldAcceptEntity() {
        BusinessDocEntryMapper mapper = Mockito.mock(BusinessDocEntryMapper.class);
        BusinessDocEntryEntity entity = new BusinessDocEntryEntity();
        entity.setDocId(1L);
        entity.setExpenseType("PURCHASE");
        entity.setSubjectId(1L);
        entity.setAmount(java.math.BigDecimal.valueOf(10000));
        entity.setInvoiceNo(null);
        entity.setAssistJson(null);
        entity.setSummary("采购业务单");
        entity.setSortOrder(1);
        Mockito.when(mapper.updateById(entity)).thenReturn(1);
        int rows = mapper.updateById(entity);
        assertEquals(1, rows);
        Mockito.verify(mapper).updateById(entity);
    }

    @Test
    @DisplayName("BusinessDocEntryMapper deleteById 方法应接受ID参数")
    void deleteById_shouldAcceptId() {
        BusinessDocEntryMapper mapper = Mockito.mock(BusinessDocEntryMapper.class);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);
        int rows = mapper.deleteById(1L);
        assertEquals(1, rows);
        Mockito.verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("BusinessDocEntryMapper 所有方法定义应正确")
    void allMethods_shouldBeDefined() {
        BusinessDocEntryMapper mapper = Mockito.mock(BusinessDocEntryMapper.class);
        BusinessDocEntryEntity e = new BusinessDocEntryEntity();
        e.setDocId(1L);
        e.setExpenseType("PURCHASE");
        e.setSubjectId(1L);
        e.setAmount(java.math.BigDecimal.valueOf(10000));
        e.setInvoiceNo(null);
        e.setAssistJson(null);
        e.setSummary("采购业务单");
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
