package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.RoleMenuEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RoleMenu Mapper 真实 DB 测试。
 *
 * <h3>历史说明（REQ-2026-120 / 慢测 B 类）</h3>
 * 原硬编码 {@code roleId=1, menuId=1}，而该组合已存在于 Flyway 种子
 * {@code t_role_menu} 中，触发 {@code uq_role_menu} 唯一键撞码。
 * 改用基类 {@code createRole()} / {@code createMenu()} 现造行。
 */
class RoleMenuMapperRealDBTest extends AbstractMapperTest {

    @Autowired
    private RoleMenuMapper roleMenuMapper;

    @Test
    @DisplayName("插入角色菜单关联应返回主键")
    void insert_shouldReturnId() {
        RoleMenuEntity entity = new RoleMenuEntity();
        entity.setRoleId(createRole("TEST-ROLE"));
        entity.setMenuId(createMenu("TEST-MENU"));

        assertEquals(1, roleMenuMapper.insert(entity));
        assertNotNull(entity.getId());
    }
}