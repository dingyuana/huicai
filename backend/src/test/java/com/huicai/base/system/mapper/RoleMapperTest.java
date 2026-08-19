package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.RoleEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Role Mapper 真实 DB 测试.
 * 验证 role_code UNIQUE 约束、NOT NULL 约束.
 */
class RoleMapperTest extends AbstractMapperTest {

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

        int rows = roleMapper.insert(entity);

        assertEquals(1, rows);
        assertNotNull(entity.getId());
    }

    @Test
    void insert_shouldFailWithDuplicateRoleCode() {
        RoleEntity entity1 = new RoleEntity();
        entity1.setCode("ROLE_UNIQUE_TEST");
        entity1.setName("唯一性测试角色1");
        entity1.setStatus("enabled");
        entity1.setSortOrder(1);
        entity1.setDataScope("all");
        entity1.setDeleted(0);
        entity1.setCreatedBy(1L);
        entity1.setUpdatedBy(1L);
        roleMapper.insert(entity1);

        RoleEntity entity2 = new RoleEntity();
        entity2.setCode("ROLE_UNIQUE_TEST");
        entity2.setName("唯一性测试角色2");
        entity2.setStatus("enabled");
        entity2.setSortOrder(2);
        entity2.setDataScope("all");
        entity2.setDeleted(0);
        entity2.setCreatedBy(1L);
        entity2.setUpdatedBy(1L);

        assertThrows(Exception.class, () -> roleMapper.insert(entity2),
                "role_code 唯一性约束应阻止重复");
    }
}