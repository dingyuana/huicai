package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.RoleMenuEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RoleMenu Mapper 真实 DB 测试.
 */
class RoleMenuMapperTest extends AbstractMapperTest {

    @Autowired
    private RoleMenuMapper roleMenuMapper;

    @Test
    void insert_shouldReturnId() {
        RoleMenuEntity entity = new RoleMenuEntity();
        entity.setRoleId(1L);
        entity.setMenuId(1L);

        int rows = roleMenuMapper.insert(entity);

        assertEquals(1, rows);
        assertNotNull(entity.getId());
    }

    @Test
    void selectById_shouldReturnRelation() {
        RoleMenuEntity entity = new RoleMenuEntity();
        entity.setRoleId(2L);
        entity.setMenuId(5L);
        roleMenuMapper.insert(entity);

        RoleMenuEntity found = roleMenuMapper.selectById(entity.getId());
        assertNotNull(found);
        assertEquals(2L, found.getRoleId());
        assertEquals(5L, found.getMenuId());
    }
}