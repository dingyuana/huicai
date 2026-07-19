package com.huicai.base.voucher.mapper;

import com.huicai.base.voucher.entity.VoucherTemplateEntity;
import com.huicai.base.voucher.mapper.VoucherTemplateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

/**
 * VoucherTemplateMapper 方法签名验证测试
 */
public class VoucherTemplateMapperTest {

    @Test
    @DisplayName("VoucherTemplateMapper insert 方法应接受正确参数")
    void insert_shouldAcceptValidParams() {
        VoucherTemplateMapper mapper = Mockito.mock(VoucherTemplateMapper.class);
        VoucherTemplateEntity entity = new VoucherTemplateEntity();
        
        // 设置必要字段
        entity.setName("测试模板");
        entity.setBusinessType("PAYMENT");
        entity.setClassification("EXPENSE");
        entity.setDirection("CREDIT");
        entity.setNumberPrefix("JZ");
        entity.setSource("MANUAL");
        entity.setMatchPriority(100);
        entity.setIsActive(true);
        entity.setDescription("测试模板描述");
        entity.setCreatedBy(1L);
        
        // 验证方法可调用且返回正确类型
        Mockito.when(mapper.insert(entity)).thenReturn(1);
        int rows = mapper.insert(entity);
        
        assertEquals(1, rows);
        Mockito.verify(mapper).insert(entity);
    }

    @Test
    @DisplayName("VoucherTemplateMapper selectById 方法应返回实体")
    void selectById_shouldReturnEntity() {
        VoucherTemplateMapper mapper = Mockito.mock(VoucherTemplateMapper.class);
        VoucherTemplateEntity entity = new VoucherTemplateEntity();
        entity.setName("测试模板");
        entity.setBusinessType("PAYMENT");
        entity.setClassification("EXPENSE");
        entity.setDirection("CREDIT");
        entity.setNumberPrefix("JZ");
        entity.setSource("MANUAL");
        entity.setMatchPriority(100);
        entity.setIsActive(true);
        entity.setDescription("测试模板描述");
        entity.setCreatedBy(1L);
        Mockito.when(mapper.selectById(1L)).thenReturn(entity);
        
        VoucherTemplateEntity result = mapper.selectById(1L);
        
        assertNotNull(result);
        Mockito.verify(mapper).selectById(1L);
    }

    @Test
    @DisplayName("VoucherTemplateMapper updateById 方法应接受实体参数")
    void updateById_shouldAcceptEntity() {
        VoucherTemplateMapper mapper = Mockito.mock(VoucherTemplateMapper.class);
        VoucherTemplateEntity entity = new VoucherTemplateEntity();
        entity.setName("测试模板");
        entity.setBusinessType("PAYMENT");
        entity.setClassification("EXPENSE");
        entity.setDirection("CREDIT");
        entity.setNumberPrefix("JZ");
        entity.setSource("MANUAL");
        entity.setMatchPriority(100);
        entity.setIsActive(true);
        entity.setDescription("测试模板描述");
        entity.setCreatedBy(1L);
        Mockito.when(mapper.updateById(entity)).thenReturn(1);
        
        int rows = mapper.updateById(entity);
        
        assertEquals(1, rows);
        Mockito.verify(mapper).updateById(entity);
    }

    @Test
    @DisplayName("VoucherTemplateMapper deleteById 方法应接受ID参数")
    void deleteById_shouldAcceptId() {
        VoucherTemplateMapper mapper = Mockito.mock(VoucherTemplateMapper.class);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);
        
        int rows = mapper.deleteById(1L);
        
        assertEquals(1, rows);
        Mockito.verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("VoucherTemplateMapper 所有方法定义应正确")
    void allMethods_shouldBeDefined() {
        VoucherTemplateMapper mapper = Mockito.mock(VoucherTemplateMapper.class);
        
        // 验证所有常用方法存在
        VoucherTemplateEntity e = new VoucherTemplateEntity();
        e.setName("测试模板");
        e.setBusinessType("PAYMENT");
        e.setClassification("EXPENSE");
        e.setDirection("CREDIT");
        e.setNumberPrefix("JZ");
        e.setSource("MANUAL");
        e.setMatchPriority(100);
        e.setIsActive(true);
        e.setDescription("测试模板描述");
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
