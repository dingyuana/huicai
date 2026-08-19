package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.MenuEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Menu Mapper 真实 DB 测试.
 */
class MenuMapperTest extends AbstractMapperTest {

    @Autowired
    private MenuMapper menuMapper;

    @Test
    void insert_shouldReturnId() {
        MenuEntity entity = new MenuEntity();
        entity.setName("测试菜单");
        entity.setPermissionCode("system:test");
        entity.setType("menu");
        entity.setParentId(0L);
        entity.setSortOrder(1);
        entity.setIsActive(true);
        entity.setDeleted(0);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);

        int rows = menuMapper.insert(entity);

        assertEquals(1, rows);
        assertNotNull(entity.getId());
    }

    @Test
    void selectById_shouldReturnMenu() {
        MenuEntity entity = new MenuEntity();
        entity.setName("测试菜单2");
        entity.setPermissionCode("system:test2");
        entity.setType("menu");
        entity.setParentId(0L);
        entity.setSortOrder(2);
        entity.setIsActive(true);
        entity.setDeleted(0);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);
        menuMapper.insert(entity);

        MenuEntity found = menuMapper.selectById(entity.getId());
        assertNotNull(found);
        assertEquals("测试菜单2", found.getName());
        assertEquals("menu", found.getType());
    }
}