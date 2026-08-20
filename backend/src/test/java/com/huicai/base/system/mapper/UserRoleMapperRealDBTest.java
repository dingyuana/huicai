package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.UserRoleEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserRole Mapper 真实 DB 测试.
 */
class UserRoleMapperRealDBTest extends AbstractMapperTest {

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Test
    void insert_shouldReturnId() {
        UserRoleEntity entity = new UserRoleEntity();
        entity.setUserId(1L);
        entity.setRoleId(1L);

        assertEquals(1, userRoleMapper.insert(entity));
        assertNotNull(entity.getId());
    }
}