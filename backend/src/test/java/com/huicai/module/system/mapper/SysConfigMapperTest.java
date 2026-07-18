package com.huicai.module.system.mapper;

import com.huicai.module.system.entity.SysConfigEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SysConfigMapper 方法签名验证测试
 */
public class SysConfigMapperTest {

    @Test
    @DisplayName("SysConfigMapper insert 方法应接受正确参数")
    void insert_shouldAcceptValidParams() {
        SysConfigMapper mapper = Mockito.mock(SysConfigMapper.class);
        SysConfigEntity entity = new SysConfigEntity();

        // 设置必要字段
        entity.setConfigKey("test.key");
        entity.setConfigValue("test_value");
        entity.setConfigType("SYSTEM");
        entity.setDescription("测试配置");
        entity.setIsActive(true);

        // 验证方法可调用且返回正确类型
        Mockito.when(mapper.insert(entity)).thenReturn(1);
        int rows = mapper.insert(entity);

        assertEquals(1, rows);
        Mockito.verify(mapper).insert(entity);
    }

    @Test
    @DisplayName("SysConfigMapper selectById 方法应返回实体")
    void selectById_shouldReturnEntity() {
        SysConfigMapper mapper = Mockito.mock(SysConfigMapper.class);
        SysConfigEntity entity = new SysConfigEntity();
        entity.setConfigKey("test.key");
        entity.setConfigValue("test_value");
        entity.setConfigType("SYSTEM");
        entity.setDescription("测试配置");
        entity.setIsActive(true);
        Mockito.when(mapper.selectById(1L)).thenReturn(entity);

        SysConfigEntity result = mapper.selectById(1L);

        assertNotNull(result);
        Mockito.verify(mapper).selectById(1L);
    }

    @Test
    @DisplayName("SysConfigMapper updateById 方法应接受实体参数")
    void updateById_shouldAcceptEntity() {
        SysConfigMapper mapper = Mockito.mock(SysConfigMapper.class);
        SysConfigEntity entity = new SysConfigEntity();
        entity.setConfigKey("test.key");
        entity.setConfigValue("test_value");
        entity.setConfigType("SYSTEM");
        entity.setDescription("测试配置");
        entity.setIsActive(true);
        Mockito.when(mapper.updateById(entity)).thenReturn(1);

        int rows = mapper.updateById(entity);

        assertEquals(1, rows);
        Mockito.verify(mapper).updateById(entity);
    }

    @Test
    @DisplayName("SysConfigMapper deleteById 方法应接受ID参数")
    void deleteById_shouldAcceptId() {
        SysConfigMapper mapper = Mockito.mock(SysConfigMapper.class);
        Mockito.when(mapper.deleteById(1L)).thenReturn(1);

        int rows = mapper.deleteById(1L);

        assertEquals(1, rows);
        Mockito.verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("SysConfigMapper 所有方法定义应正确")
    void allMethods_shouldBeDefined() {
        SysConfigMapper mapper = Mockito.mock(SysConfigMapper.class);

        // 验证所有常用方法存在
        SysConfigEntity e = new SysConfigEntity();
        e.setConfigKey("test.key");
        e.setConfigValue("test_value");
        e.setConfigType("SYSTEM");
        e.setDescription("测试配置");
        e.setIsActive(true);
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
