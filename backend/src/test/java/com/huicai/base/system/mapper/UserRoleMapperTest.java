package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.UserRoleEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserRole Mapper 真实 DB 测试.
 */
class UserRoleMapperTest extends AbstractMapperTest {

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Test
    void insert_shouldReturnId() {
        UserRoleEntity entity = new UserRoleEntity();
        entity.setUserId(1L);
        entity.setRoleId(1L);

        int rows = userRoleMapper.insert(entity);

        assertEquals(1, rows);
        assertNotNull(entity.getId());
    }

    @Test
    void selectById_shouldReturnRelation() {
        UserRoleEntity entity = new UserRoleEntity();
        entity.setUserId(2L);
        entity.setRoleId(3L);
        userRoleMapper.insert(entity);

        UserRoleEntity found = userRoleMapper.selectById(entity.getId());
        assertNotNull(found);
        assertEquals(2L, found.getUserId());
        assertEquals(3L, found.getRoleId());
    }
}