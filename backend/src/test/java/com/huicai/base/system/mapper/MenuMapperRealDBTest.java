package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.MenuEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Menu Mapper 真实 DB 测试.
 */
class MenuMapperRealDBTest extends AbstractMapperTest {

    @Autowired
    private MenuMapper menuMapper;

    @Test
    void insert_shouldReturnId() {
        MenuEntity entity = new MenuEntity();
        entity.setName("测试菜单");
        entity.setPermissionCode("system:test_" + System.currentTimeMillis());
        entity.setType("menu");
        entity.setParentId(0L);
        entity.setSortOrder(1);
        entity.setIsActive(true);
        entity.setDeleted(0);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);

        assertEquals(1, menuMapper.insert(entity));
        assertNotNull(entity.getId());
    }
}