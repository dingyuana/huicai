package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.SysConfigEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SysConfig Mapper 真实 DB 测试.
 */
class SysConfigMapperTest extends AbstractMapperTest {

    @Autowired
    private SysConfigMapper sysConfigMapper;

    @Test
    void insert_shouldReturnId() {
        SysConfigEntity entity = new SysConfigEntity();
        entity.setConfigKey("test_config_" + System.currentTimeMillis());
        entity.setConfigValue("test_value");
        entity.setConfigType("system");
        entity.setDescription("测试配置");
        entity.setIsActive(true);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);

        int rows = sysConfigMapper.insert(entity);

        assertEquals(1, rows);
        assertNotNull(entity.getId());
    }

    @Test
    void selectById_shouldReturnConfig() {
        SysConfigEntity entity = new SysConfigEntity();
        entity.setConfigKey("test_config_2_" + System.currentTimeMillis());
        entity.setConfigValue("test_value_2");
        entity.setConfigType("business");
        entity.setDescription("测试配置2");
        entity.setIsActive(true);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        sysConfigMapper.insert(entity);

        SysConfigEntity found = sysConfigMapper.selectById(entity.getId());
        assertNotNull(found);
        assertEquals("test_value_2", found.getConfigValue());
        assertEquals("business", found.getConfigType());
    }
}