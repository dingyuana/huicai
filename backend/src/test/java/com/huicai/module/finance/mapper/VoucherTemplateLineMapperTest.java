package com.huicai.module.finance.mapper;

import com.huicai.module.finance.entity.VoucherTemplateLineEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

public class VoucherTemplateLineMapperTest {

    @Test
    @DisplayName("VoucherTemplateLineMapper insert 方法应接受正确参数")
    void insert_shouldAcceptValidParams() {
        VoucherTemplateLineMapper mapper = Mockito.mock(VoucherTemplateLineMapper.class);
        VoucherTemplateLineEntity entity = new VoucherTemplateLineEntity();
        entity.setTemplateId(1L);
        entity.setSubjectId(1L);
        entity.setDrAmountTemplate("amount");
        entity.setCrAmountTemplate("amount");
        entity.setSummaryTemplate("摘要");
        entity.setDirection("DEBIT");
        entity.setAssistType(null);
        entity.setAssistRequired(false);
        entity.setLineOrder(1);
        Mockito.when(mapper.insert(entity)).thenReturn(1);
        int rows = mapper.insert(entity);
        assertEquals(1, rows);
        Mockito.verify(mapper).insert(entity);
    }

    @Test
    @DisplayName("VoucherTemplateLineMapper selectById 方法应返回实体")
    void selectById_shouldReturnEntity() {
        VoucherTemplateLineMapper mapper = Mockito.mock(VoucherTemplateLineMapper.class);
        VoucherTemplateLineEntity entity = new VoucherTemplateLineEntity();
        entity.setTemplateId(1L);
        entity.setSubjectId(1L);
        entity.setDrAmountTemplate("amount");
        entity.setCrAmountTemplate("amount");
        entity.setSummaryTemplate("摘要");
        entity.setDirection("DEBIT");
        entity.setAssistType(null);
        entity.setAssistRequired(false);
        entity.setLineOrder(1);
        Mockito.when(mapper.selectById(1L)).thenReturn(entity);
        VoucherTemplateLineEntity result = mapper.selectById(1L);
        assertNotNull(result);
        Mockito.verify(mapper).selectById(1L);
    }

    @Test
    @DisplayName("VoucherTemplateLineMapper updateById 方法应接受实体参数")
    void updateById_shouldAcceptEntity() {
        VoucherTemplateLineMapper mapper = Mockito.mock(VoucherTemplateLineMapper.class);
        VoucherTemplateLineEntity entity = new VoucherTemplateLineEntity();
        entity.setTemplateId(1L);
        entity.setSubjectId(1L);
        entity.setDrAmountTemplate("amount");
        entity.setCrAmountTemplate("amount");
        entity.setSummaryTemplate("摘要");
        entity.setDirection("DEBIT");
        entity.setAssistType(null);
        entity.setAssistRequired(false);
        entity.setLineOrder(1);
        Mockito.when(mapper.updateById(entity)).thenReturn(1);
        int rows = mapper.updateById(entity);
        assertEquals(1, rows);
        Mockito.verify(mapper).updateById(entity);
    }

    @Test
    @DisplayName("VoucherTemplateLineMapper deleteById 方法应接受ID参数")
    void deleteById_shouldAcceptId() {
        VoucherTemplateLineMapper mapper = Mockito.mock(VoucherTemplateLineMapper.class);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);
        int rows = mapper.deleteById(1L);
        assertEquals(1, rows);
        Mockito.verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("VoucherTemplateLineMapper 所有方法定义应正确")
    void allMethods_shouldBeDefined() {
        VoucherTemplateLineMapper mapper = Mockito.mock(VoucherTemplateLineMapper.class);
        VoucherTemplateLineEntity e = new VoucherTemplateLineEntity();
        e.setTemplateId(1L);
        e.setSubjectId(1L);
        e.setDrAmountTemplate("amount");
        e.setCrAmountTemplate("amount");
        e.setSummaryTemplate("摘要");
        e.setDirection("DEBIT");
        e.setAssistType(null);
        e.setAssistRequired(false);
        e.setLineOrder(1);
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
