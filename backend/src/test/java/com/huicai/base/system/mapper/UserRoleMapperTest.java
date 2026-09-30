package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.UserRoleEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserRole Mapper 真实 DB 测试。
 *
 * <h3>历史说明（REQ-2026-120 / 慢测 B 类）</h3>
 * 原硬编码 {@code userId=1, roleId=1}（及 {@code 2,3}），而 {@code (1,1)}
 * 已存在于 Flyway 种子 {@code t_user_role} 中，触发 {@code uq_user_role} 撞码。
 * 改用基类 {@code createSysUser()} / {@code createRole()} 现造行。
 */
class UserRoleMapperTest extends AbstractMapperTest {

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Test
    @DisplayName("插入用户角色关联应返回主键")
    void insert_shouldReturnId() {
        UserRoleEntity entity = new UserRoleEntity();
        entity.setUserId(createSysUser("TEST-USER"));
        entity.setRoleId(createRole("TEST-ROLE"));

        int rows = userRoleMapper.insert(entity);

        assertEquals(1, rows);
        assertNotNull(entity.getId());
    }

    @Test
    @DisplayName("按主键回查应返回正确的用户与角色")
    void selectById_shouldReturnRelation() {
        Long userId = createSysUser("TEST-USER");
        Long roleId = createRole("TEST-ROLE");

        UserRoleEntity entity = new UserRoleEntity();
        entity.setUserId(userId);
        entity.setRoleId(roleId);
        userRoleMapper.insert(entity);

        UserRoleEntity found = userRoleMapper.selectById(entity.getId());
        assertNotNull(found);
        assertEquals(userId, found.getUserId());
        assertEquals(roleId, found.getRoleId());
    }
}