package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.RoleMenuEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RoleMenu Mapper 真实 DB 测试.
 */
class RoleMenuMapperRealDBTest extends AbstractMapperTest {

    @Autowired
    private RoleMenuMapper roleMenuMapper;

    @Test
    void insert_shouldReturnId() {
        RoleMenuEntity entity = new RoleMenuEntity();
        entity.setRoleId(1L);
        entity.setMenuId(1L);

        assertEquals(1, roleMenuMapper.insert(entity));
        assertNotNull(entity.getId());
    }
}