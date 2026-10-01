package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.MenuEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Menu Mapper 真实 DB 测试.
 *
 * <h3>历史说明（REQ-2026-121）</h3>
 * {@code t_menu.menu_code} 是 NOT NULL 且无默认值，而 {@code MenuEntity} 原先
 * <b>没有 menuCode 字段</b>，导致任何经 MyBatis-Plus 插入菜单的路径都必然失败
 * （含生产 {@code MenuServiceImpl.create}）。已在 REQ-2026-121 补齐 Entity 字段。
 */
class MenuMapperRealDBTest extends AbstractMapperTest {

    @Autowired
    private MenuMapper menuMapper;

    @Test
    void insert_shouldReturnId() {
        MenuEntity entity = new MenuEntity();
        entity.setName("测试菜单");
        entity.setMenuCode("MENU_TEST_" + System.nanoTime());
        entity.setPermissionCode("system:test_" + System.currentTimeMillis());
        // chk_menu_type 允许集为大写 MENU / BUTTON / DIR
        entity.setType("MENU");
        entity.setParentId(0L);
        entity.setSortOrder(1);
        entity.setIsActive(true);
        entity.setDeleted(0);
        entity.setCreatedBy(1L);
        entity.setUpdatedBy(1L);

        assertEquals(1, menuMapper.insert(entity));
        assertNotNull(entity.getId());
    }

    // ===== 以下为 P104 第 2 批补充（REQ-2026-131：Mock 同义反复归零）=====
    // 原 MenuMapperTest 用 mock(MenuMapper.class) mock 被测对象本身，
    // 断言的是「我写的桩被返回」，下面这些断言它一条都做不到。

    private MenuEntity newMenu(String suffix, String type) {
        MenuEntity m = new MenuEntity();
        m.setName("补充测试菜单-" + suffix);
        m.setMenuCode("P104_MENU_" + suffix + "_" + System.nanoTime());
        m.setPermissionCode("p104:test:" + suffix + ":" + System.currentTimeMillis());
        m.setType(type);
        m.setParentId(0L);
        m.setSortOrder(1);
        m.setIsActive(true);
        return m;
    }

    @Test
    @DisplayName("menu_type 小写值必须被 CHECK 拒绝（Mock 版永远发现不了）")
    void lowercaseMenuTypeViolatesCheckConstraint() {
        // chk_menu_type 允许集是大写 MENU / BUTTON / DIR。
        // AGENTS §4.2 第 14 条记载：曾有代码按小写 "menu" 过滤导致用户路由恒空，
        // 而当时的测试从未覆盖这个约束。
        MenuEntity bad = newMenu("LOWER", "menu");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> menuMapper.insert(bad),
                "小写 menu_type 未被 CHECK 拒绝 ⇒ 允许集被放宽或 CHECK 失效");
    }

    @Test
    @DisplayName("uq_menu_code 唯一约束真实生效")
    void menuCodeIsUnique() {
        String code = "P104_MENU_DUP_" + System.nanoTime();
        MenuEntity first = newMenu("DUPA", "MENU");
        first.setMenuCode(code);
        menuMapper.insert(first);

        MenuEntity second = newMenu("DUPB", "MENU");
        second.setMenuCode(code);
        assertThrows(org.springframework.dao.DuplicateKeyException.class,
                () -> menuMapper.insert(second),
                "重复 menu_code 未被拒绝");
    }

    @Test
    @DisplayName("🔴 deleteById 是软删除：行仍在但 deleted=1")
    void deleteByIdIsSoftDelete() {
        MenuEntity m = newMenu("DEL", "MENU");
        menuMapper.insert(m);
        Long id = m.getId();

        assertEquals(1, menuMapper.deleteById(id));

        Integer physical = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_menu WHERE id = ?", Integer.class, id);
        assertEquals(1, physical, "行被物理删除 —— 违反逻辑删除铁律 #12");

        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM t_menu WHERE id = ?", Integer.class, id);
        assertEquals(1, deleted, "deleted 未置 1，不是软删除");
    }

    @Test
    @DisplayName("租户隔离：t_menu 属共享表，不应被注入 enterprise_id 条件")
    void menuIsSharedTableNotTenantFiltered() {
        MenuEntity m = newMenu("SHARED", "MENU");
        menuMapper.insert(m);

        // t_menu 在 EnterpriseDataPermissionInterceptor.SHARED_TABLES 中，
        // 菜单是全租户共享的主数据；若被误加租户过滤，用户路由会跨租户错乱。
        useEnterprise(987654L);
        assertNotNull(menuMapper.selectById(m.getId()),
                "共享表 t_menu 被按租户过滤了 —— 菜单应全租户可见");
    }
}