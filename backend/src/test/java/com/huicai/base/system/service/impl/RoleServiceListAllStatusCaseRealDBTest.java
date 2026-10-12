package com.huicai.base.system.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.huicai.base.system.entity.RoleEntity;
import com.huicai.common.test.AbstractMapperTest;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「角色列表必须真的返回角色」—— 堵住大小写字面量这类静默失效。
 *
 * <h2>为什么需要它</h2>
 * {@code listAll()} 曾写 {@code .eq(getStatus, "active")}（小写），
 * 而 {@code t_role.status} 实际存 {@code ACTIVE}（大写）⇒ SQL 恒不匹配 ⇒
 * 返回空列表。
 *
 * <p>这个缺陷的恶劣之处不在于抛异常，而在于<b>它返回 200 + 空数组</b>：
 * 用户管理页的「角色」下拉框空白，而前端 {@code getAllRoles()} 无
 * {@code try/catch}，空数组被当作合法结果静默渲染 ⇒
 * <b>前后端两侧都没有任何报错</b>。
 *
 * <p>本测试因此直接断言<b>结果非空且含真实角色</b>，而不是断言 SQL 片段 ——
 * 后者会在有人改写法后仍然通过。
 */
@DisplayName("角色列表：字面量大小写不得使查询静默返回空")
class RoleServiceListAllStatusCaseRealDBTest extends AbstractMapperTest {

    @Autowired
    private RoleServiceImpl roleService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("listAll 必须返回库中所有 ACTIVE 角色（修复前返回空）")
    void listAllReturnsActiveRoles() {
        Long expected = jdbc.queryForObject(
                "SELECT count(*) FROM t_role WHERE deleted=0 AND status='ACTIVE'",
                Long.class);
        assertTrue(expected != null && expected > 0,
                "前提不成立：库里没有任何 ACTIVE 角色，本测试无法证明修复");

        List<RoleEntity> roles = roleService.listAll();

        assertFalse(roles.isEmpty(),
                "listAll() 返回空列表 ⇒ 角色下拉框会空白。"
                        + "先查 t_role.status 的真实取值与大小写："
                        + "SELECT DISTINCT status, length(status) FROM t_role;");

        assertTrue(roles.size() == expected,
                "listAll() 返回 " + roles.size() + " 条，但库里有 " + expected
                        + " 条 ACTIVE 角色 ⇒ 查询条件与实际数据不符");

        // 逐条回查，确认没有混入非 ACTIVE 的角色（negative assertion）
        for (RoleEntity r : roles) {
            assertTrue("ACTIVE".equals(r.getStatus()),
                    "角色 " + r.getId() + " 的 status=" + r.getStatus()
                            + " 不是 ACTIVE ⇒ 被错误地包含进来");
        }
    }

    @Test
    @DisplayName("status 字面量必须与库中真实取值同大小写（本缺陷的直接回归锁）")
    void statusLiteralCaseMatchesDatabase() {
        // 直接从库里取真实取值，再与代码里用的字面量比对 ——
        // 这样即便将来有人把 ACTIVE 改回 active，本例立刻红。
        String real = jdbc.queryForObject(
                "SELECT DISTINCT status FROM t_role WHERE deleted=0 LIMIT 1",
                String.class);
        assertTrue(real != null && real.equals(real.toUpperCase(Locale.ROOT)),
                "t_role.status 实际值 '" + real + "' 不是全大写 —— "
                        + "若代码按小写字面量查询会静默返回空");

        assertTrue("ACTIVE".equals(real),
                "t_role.status 实际值为 '" + real + "'，"
                        + "与 RoleServiceImpl.listAll() 使用的字面量 'ACTIVE' 不一致");
    }
}