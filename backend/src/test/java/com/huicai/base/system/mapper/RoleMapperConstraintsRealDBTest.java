package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.RoleEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RoleMapper 真库补充（REQ-2026-131 / P104 第 3 批）
 *
 * <p>{@code RoleMapperTest} 用 mock mock 被测对象本身；其中
 * 「deleteById 应接受 ID 参数」尤其误导 —— mock 根本不会执行软删除，
 * 真实行为（铁律 #12）从未被覆盖。
 *
 * <h3>⚠️ 顺带记录一处 Entity-DB 反向缺口（AGENTS §4.2 第 16 条）</h3>
 * {@code t_role} 有 {@code role_type} 与 {@code is_system} 两列，但
 * {@code RoleEntity} <b>一个字段都没声明</b>。后果：
 * <ul>
 *   <li>DB 默认 {@code role_type='NORMAL'}、{@code is_system=false}；</li>
 *   <li>而种子行的 role_type 是 ADMIN/FINANCE_MGR/ACCOUNTANT/CASHIER/OPERATOR，</li>
 *   <li>⇒ <b>任何经 MyBatis-Plus 创建的角色都只能是 NORMAL</b>，
 *       {@code chk_role_type} 对应用侧路径实际不可达。</li>
 * </ul>
 * 本类以「如实刻画当前行为」的方式锁定该现状，补齐字段属独立需求
 * （会改变角色创建语义，须评估 `RoleService` 与前端选项）。
 */
@DisplayName("P104 RoleMapper 真库补充")
class RoleMapperConstraintsRealDBTest extends AbstractMapperTest {

    @Autowired
    private RoleMapper roleMapper;

    private RoleEntity newRole(String suffix) {
        RoleEntity r = new RoleEntity();
        r.setCode("P104_ROLE_" + suffix + "_" + System.nanoTime());
        r.setName("P104角色-" + suffix);
        r.setStatus("ACTIVE");
        r.setSortOrder(1);
        r.setDataScope("SELF");
        r.setDeleted(0);
        return r;
    }

    @Test
    @DisplayName("uq_role_code 唯一约束真实生效")
    void roleCodeIsUnique() {
        String code = "P104_ROLE_DUP_" + System.nanoTime();
        RoleEntity first = newRole("DUPA");
        first.setCode(code);
        roleMapper.insert(first);

        RoleEntity second = newRole("DUPB");
        second.setCode(code);
        assertThrows(DuplicateKeyException.class, () -> roleMapper.insert(second),
                "重复 role_code 未被拒绝");
    }

    @Test
    @DisplayName("🔴 deleteById 是软删除：角色不可物理删除（授权关系依赖它）")
    void deleteByIdIsSoftDelete() {
        RoleEntity r = newRole("DEL");
        roleMapper.insert(r);
        Long id = r.getId();

        assertEquals(1, roleMapper.deleteById(id));
        Integer physical = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_role WHERE id = ?", Integer.class, id);
        assertEquals(1, physical, "角色被物理删除 —— 违反逻辑删除铁律 #12");
        assertNull(roleMapper.selectById(id));
    }

    @Test
    @DisplayName("⚠️ 记录 Entity 缺口：MP 创建的角色 role_type 恒为默认 NORMAL")
    void mpCreatedRoleAlwaysGetsDefaultRoleType() {
        RoleEntity r = newRole("TYPE");
        roleMapper.insert(r);

        String roleType = jdbcTemplate.queryForObject(
                "SELECT role_type FROM t_role WHERE id = ?", String.class, r.getId());
        Boolean isSystem = jdbcTemplate.queryForObject(
                "SELECT is_system FROM t_role WHERE id = ?", Boolean.class, r.getId());

        // 如实体将来补上 roleType 字段，此断言会失败 —— 那正是应当更新的信号
        assertEquals("NORMAL", roleType,
                "MP 创建的角色 role_type 不再是默认 NORMAL ⇒ Entity 已补该字段，请同步更新本测试与相关需求");
        assertEquals(Boolean.FALSE, isSystem, "is_system 默认应为 false");
    }

    @Test
    @DisplayName("CHK 允许集真实有效（用原生 SQL 验证，防止约束失效）")
    void checkRoleTypeConstraintIsEffective() {
        RoleEntity r = newRole("CHK");
        roleMapper.insert(r);

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        "UPDATE t_role SET role_type = 'lowercase_not_allowed' WHERE id = ?", r.getId()),
                "chk_role_type 未拒绝非法值 ⇒ 约束可能已失效");
    }

    @Test
    @DisplayName("反证：软删除后角色菜单绑定不受影响（恢复后权限语义不变）")
    void softDeletedRoleKeepsRoleMenuBinding() {
        RoleEntity r = newRole("BIND");
        roleMapper.insert(r);
        Long menuId = jdbcTemplate.queryForObject(
                "SELECT id FROM t_menu WHERE deleted = 0 ORDER BY id LIMIT 1", Long.class);
        jdbcTemplate.update(
                "INSERT INTO t_role_menu (role_id, menu_id, created_at) VALUES (?, ?, NOW())",
                r.getId(), menuId);
        roleMapper.deleteById(r.getId());

        Integer stillBound = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_role_menu WHERE role_id = ? AND menu_id = ?",
                Integer.class, r.getId(), menuId);
        assertEquals(1, stillBound, "角色被软删后其授权绑定丢失 ⇒ 恢复后权限语义已变");
        assertTrue(roleMapper.selectById(r.getId()) == null, "软删除后不应查得到");
        assertNotNull(menuId);
    }
}