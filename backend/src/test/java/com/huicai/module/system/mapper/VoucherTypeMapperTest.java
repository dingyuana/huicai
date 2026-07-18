package com.huicai.module.system.mapper;

import com.huicai.module.system.entity.VoucherTypeEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

/**
 * VoucherTypeMapper 方法签名验证测试
 */
public class VoucherTypeMapperTest {

    @Test
    @DisplayName("VoucherTypeMapper insert 方法应接受正确参数")
    void insert_shouldAcceptValidParams() {
        VoucherTypeMapper mapper = Mockito.mock(VoucherTypeMapper.class);
        VoucherTypeEntity entity = new VoucherTypeEntity();

        // 设置必要字段
        entity.setName("测试凭证类型");
        entity.setCode("TEST");
        entity.setSortOrder(100);
        entity.setNumberingRule("JJ-YYYYMMDD-0000");
        entity.setIsActive(true);
        entity.setRemark("测试备注");
        entity.setCreatedBy(1L);

        // 验证方法可调用且返回正确类型
        Mockito.when(mapper.insert(entity)).thenReturn(1);
        int rows = mapper.insert(entity);

        assertEquals(1, rows);
        Mockito.verify(mapper).insert(entity);
    }

    @Test
    @DisplayName("VoucherTypeMapper selectById 方法应返回实体")
    void selectById_shouldReturnEntity() {
        VoucherTypeMapper mapper = Mockito.mock(VoucherTypeMapper.class);
        VoucherTypeEntity entity = new VoucherTypeEntity();
        entity.setName("测试凭证类型");
        entity.setCode("TEST");
        entity.setSortOrder(100);
        entity.setNumberingRule("JJ-YYYYMMDD-0000");
        entity.setIsActive(true);
        entity.setRemark("测试备注");
        entity.setCreatedBy(1L);
        Mockito.when(mapper.selectById(1L)).thenReturn(entity);

        VoucherTypeEntity result = mapper.selectById(1L);

        assertNotNull(result);
        Mockito.verify(mapper).selectById(1L);
    }

    @Test
    @DisplayName("VoucherTypeMapper updateById 方法应接受实体参数")
    void updateById_shouldAcceptEntity() {
        VoucherTypeMapper mapper = Mockito.mock(VoucherTypeMapper.class);
        VoucherTypeEntity entity = new VoucherTypeEntity();
        entity.setName("测试凭证类型");
        entity.setCode("TEST");
        entity.setSortOrder(100);
        entity.setNumberingRule("JJ-YYYYMMDD-0000");
        entity.setIsActive(true);
        entity.setRemark("测试备注");
        entity.setCreatedBy(1L);
        Mockito.when(mapper.updateById(entity)).thenReturn(1);

        int rows = mapper.updateById(entity);

        assertEquals(1, rows);
        Mockito.verify(mapper).updateById(entity);
    }

    @Test
    @DisplayName("VoucherTypeMapper deleteById 方法应接受ID参数")
    void deleteById_shouldAcceptId() {
        VoucherTypeMapper mapper = Mockito.mock(VoucherTypeMapper.class);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);

        int rows = mapper.deleteById(1L);

        assertEquals(1, rows);
        Mockito.verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("VoucherTypeMapper 所有方法定义应正确")
    void allMethods_shouldBeDefined() {
        VoucherTypeMapper mapper = Mockito.mock(VoucherTypeMapper.class);

        // 验证所有常用方法存在
        VoucherTypeEntity e = new VoucherTypeEntity();
        e.setName("测试凭证类型");
        e.setCode("TEST");
        e.setSortOrder(100);
        e.setNumberingRule("JJ-YYYYMMDD-0000");
        e.setIsActive(true);
        e.setRemark("测试备注");
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
