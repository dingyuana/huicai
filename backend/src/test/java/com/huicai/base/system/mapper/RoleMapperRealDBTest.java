package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.RoleEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Role Mapper 真实 DB 测试.
 */
class RoleMapperRealDBTest extends AbstractMapperTest {

    @Autowired
    private RoleMapper roleMapper;

    @Test
    void insert_shouldReturnId() {
        RoleEntity entity = new RoleEntity();
        entity.setCode("ROLE_TEST_" + System.currentTimeMillis());
        entity.setName("测试角色");
        entity.setStatus("enabled");
        entity.setSortOrder(1);
        entity.setDataScope("all");
        entity.setDeleted(0);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);

        assertEquals(1, roleMapper.insert(entity));
        assertNotNull(entity.getId());
    }

    @Test
    void insert_shouldFailWithDuplicateRoleCode() {
        RoleEntity e1 = new RoleEntity();
        e1.setCode("ROLE_UNIQUE_" + System.currentTimeMillis());
        e1.setName("唯一性测试1");
        e1.setStatus("enabled");
        e1.setSortOrder(1);
        e1.setDataScope("all");
        e1.setDeleted(0);
        e1.setCreatedBy(1L);
        e1.setUpdatedBy(1L);
        roleMapper.insert(e1);

        RoleEntity e2 = new RoleEntity();
        e2.setCode(e1.getCode());
        e2.setName("唯一性测试2");
        e2.setStatus("enabled");
        e2.setSortOrder(2);
        e2.setDataScope("all");
        e2.setDeleted(0);
        e2.setCreatedBy(1L);
        e2.setUpdatedBy(1L);
        assertThrows(Exception.class, () -> roleMapper.insert(e2),
                "role_code 唯一性约束");
    }
}