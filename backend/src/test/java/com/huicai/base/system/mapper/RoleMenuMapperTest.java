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
 * 本测试硬编码 {@code roleId=1, menuId=1}（及 {@code 2,5}），
 * 而这些组合已存在于 Flyway 种子数据中（{@code t_role_menu} 种子含 role_id=1 与
 * menu_id=1~5 的组合），触发 {@code uq_role_menu} 业务唯一键撞码。
 * 改用基类 {@code createRole()} / {@code createMenu()} 现造行，
 * 保证组合必然唯一。
 */
class RoleMenuMapperTest extends AbstractMapperTest {

    @Autowired
    private RoleMenuMapper roleMenuMapper;

    @Test
    @DisplayName("插入角色菜单关联应返回主键")
    void insert_shouldReturnId() {
        RoleMenuEntity entity = new RoleMenuEntity();
        entity.setRoleId(createRole("TEST-ROLE"));
        entity.setMenuId(createMenu("TEST-MENU"));

        int rows = roleMenuMapper.insert(entity);

        assertEquals(1, rows);
        assertNotNull(entity.getId());
    }

    @Test
    @DisplayName("按主键回查应返回正确的角色与菜单")
    void selectById_shouldReturnRelation() {
        Long roleId = createRole("TEST-ROLE");
        Long menuId = createMenu("TEST-MENU");

        RoleMenuEntity entity = new RoleMenuEntity();
        entity.setRoleId(roleId);
        entity.setMenuId(menuId);
        roleMenuMapper.insert(entity);

        RoleMenuEntity found = roleMenuMapper.selectById(entity.getId());
        assertNotNull(found);
        assertEquals(roleId, found.getRoleId());
        assertEquals(menuId, found.getMenuId());
    }
}
