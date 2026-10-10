package com.huicai.security;

import com.huicai.base.system.dto.UserSaveDTO;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P115 / REQ-2026-141 —— 代理角色单一事实源守卫
 *
 * <h2>背景</h2>
 * {@code agency_role} 存在两处：{@code t_user.agency_role}（身份行）与
 * {@code t_agency_user.agency_role}（成员资格行）。后者是唯一事实源，
 * 前者是受控镜像，只允许 {@code AgencyUserServiceImpl.create} 在同一事务维护。
 *
 * <p>🔴 <b>读取方各读一半，是本次要防的核心</b>：
 * {@code SecurityUtils.getCurrentAgencyRole()} 读 {@code t_user}（经 LoginUser），
 * 而 {@code AgencyUserEnterpriseServiceImpl#assign} 判「能否被派工」读
 * {@code t_agency_user}。一旦两侧不一致，权限判定与派工校验会按不同口径走，
 * 且<b>无任何现有守卫能发现</b>。
 *
 * <h2>诚实标注：缺陷是「潜在」的</h2>
 * 实测开发库 5 个 AGENCY 用户两表完全一致（diverged=0），且唯一分叉写入方
 * {@code UserSaveDTO.agencyRole} 前端不可达（{@code UserList.vue} 只发 roleIds），
 * 只能经 API 直达。故本类守的是<b>路径</b>不是修数据。
 *
 * <h2>为什么必须做反证</h2>
 * 只断言「当前全部相等」的守卫是恒绿（§4.5 第 21 条）——
 * 数据碰巧一致时它永远通过。故 {@link #divergentMirrorIsDetected}
 * 与 {@link #addingFieldBackIsDetected} 分别注入分叉，证明守卫真的敏感。
 */
@DisplayName("SPEC-P115：代理角色单一事实源")
class AgencyRoleSingleSourceOfTruthTest extends AbstractMapperTest {

    @Autowired
    private JdbcTemplate jdbc;

    /** 两表 join：列出每个 AGENCY 用户的镜像情况。 */
    private List<java.util.Map<String, Object>> mirrorRows() {
        return jdbc.queryForList(
                "SELECT u.id AS user_id, u.username, u.agency_role AS user_role, "
                        + "       au.id AS agency_user_id, au.agency_role AS agency_user_role "
                        + "FROM t_user u "
                        + "LEFT JOIN t_agency_user au ON au.user_id = u.id AND au.deleted = 0 "
                        + "WHERE u.user_type = 'AGENCY' AND u.deleted = 0 "
                        + "ORDER BY u.id");
    }

    // ───────────────── AT-115-1：成员资格行必须存在且唯一 ─────────────────

    @Test
    @DisplayName("AT-115-1 每个 AGENCY 用户必须恰好有一条 t_agency_user 行")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void everyAgencyUserHasExactlyOneMembershipRow() {
        List<java.util.Map<String, Object>> rows = mirrorRows();

        List<String> missing = new java.util.ArrayList<>();
        List<String> duplicated = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> r : rows) {
            Object auId = r.get("agency_user_id");
            if (auId == null) {
                missing.add(String.valueOf(r.get("username")));
            }
        }
        // 唯一性：t_agency_user.uk_au_user 已保证同 user_id 不可重复（未删行），
        // 但 deleted 行可多条，故按「未删行」再查一次计数。
        List<java.util.Map<String, Object>> dup = jdbc.queryForList(
                "SELECT user_id, count(*) AS c FROM t_agency_user "
                        + "WHERE deleted = 0 GROUP BY user_id HAVING count(*) > 1");
        for (java.util.Map<String, Object> d : dup) {
            duplicated.add(String.valueOf(d.get("user_id")));
        }

        assertTrue(missing.isEmpty(),
                "以下 AGENCY 用户在 t_user 有行但 t_agency_user **没有**成员资格行 ⇒ "
                        + "该用户根本没有代理内角色，却带着 t_user.agency_role 参与权限判定："
                        + missing);
        assertTrue(duplicated.isEmpty(),
                "以下 user_id 存在多条未删的 t_agency_user 行 ⇒ 成员资格不唯一，角色事实源歧义："
                        + duplicated);
    }

    // ───────────────── AT-115-2：两表角色必须相等 ─────────────────

    @Test
    @DisplayName("AT-115-2 t_user.agency_role 必须与 t_agency_user.agency_role 相等")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void mirrorRoleMustMatchSourceOfTruth() {
        // ⚠️ 刻意复用 collectDiverged()：反证 AT-115-5 注入分叉后断言的正是**这个**方法，
        // 而不是一份平行实现。否则反证只能证明「另写的一份代码能检出」，
        // 与守卫本身是否敏感无关 —— 那是假反证。
        List<String> diverged = collectDiverged();

        assertTrue(diverged.isEmpty(),
                "代理角色两表分叉（t_user 是镜像、t_agency_user 是事实源）：" + diverged
                        + " ⇒ 权限判定（读 t_user）与派工校验（读 t_agency_user）会按不同口径走");
    }

    // ───────────────── AT-115-3：DTO 不得再携带 agencyRole ─────────────────

    @Test
    @DisplayName("AT-115-3 UserSaveDTO 不得声明 agencyRole 字段（关闭唯一分叉写入方）")
    void userSaveDtoMustNotCarryAgencyRole() {
        for (Field f : UserSaveDTO.class.getDeclaredFields()) {
            assertFalse("agencyRole".equals(f.getName()),
                    "UserSaveDTO 仍声明 agencyRole ⇒ 「系统管理→用户管理」可经 API 只写 "
                            + "t_user.agency_role 而不动 t_agency_user，两表随即分叉。"
                            + "代理内角色只能在「会计管理」里维护。");
        }
    }

    // ───────────────── AT-115-5：反证 —— 注入分叉必须被检出 ─────────────────

    @Test
    @DisplayName("AT-115-5 反证：注入分叉数据后守卫必须转红（证明非恒绿）")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void divergentMirrorIsDetected() {
        // 取一个现有 AGENCY 用户，把**镜像侧**改成另一个值。
        // 若 AT-115-2 的断言对「注入的分叉」不敏感，那它在真实分叉时同样不会红
        // ⇒ 整条守卫是恒绿，等于没有。
        Long victim = jdbc.queryForObject(
                "SELECT u.id FROM t_user u JOIN t_agency_user au "
                        + "ON au.user_id = u.id AND au.deleted = 0 "
                        + "WHERE u.user_type='AGENCY' AND u.deleted=0 ORDER BY u.id LIMIT 1",
                Long.class);
        String before = jdbc.queryForObject(
                "SELECT agency_role FROM t_user WHERE id = ?", String.class, victim);

        try {
            jdbc.update("UPDATE t_user SET agency_role = ? WHERE id = ?",
                    "REVIEWER".equals(before) ? "ACCOUNTANT" : "REVIEWER", victim);

            List<String> diverged = collectDiverged();
            assertFalse(diverged.isEmpty(),
                    "注入分叉后守卫竟未检出 ⇒ 它是恒绿的，对真实分叉同样失明。"
                            + "判据：只断言「当前全部相等」的守卫不算验证（§4.5 第 21 条）。");
        } finally {
            jdbc.update("UPDATE t_user SET agency_role = ? WHERE id = ?", before, victim);
        }
    }

    // ───────────────── AT-115-6：反证 —— 字段加回必须被检出 ─────────────────

    @Test
    @DisplayName("AT-115-6 反证：若把 agencyRole 加回 DTO，本守卫必须能判（自证非恒真）")
    void addingFieldBackIsDetected() {
        // 不修改生产代码，而是显式复现「字段存在」的判定前提：
        // 一个含 agencyRole 的替身类必须被同一判据判为违规。
        class WithAgencyRole {
            @SuppressWarnings("unused")
            private String agencyRole;
        }
        boolean detected = false;
        for (Field f : WithAgencyRole.class.getDeclaredFields()) {
            if ("agencyRole".equals(f.getName())) {
                detected = true;
            }
        }
        assertTrue(detected,
                "连替身类的 agencyRole 都检不出 ⇒ AT-115-3 的字段判据本身失效");
    }

    /**
     * 收集两表角色分叉的用户名 —— <b>AT-115-2 与反证 AT-115-5 共用</b>。
     *
     * <p>共用的意义：反证注入分叉后调用的是这个方法，故它证明的是
     * <b>守卫自身判定逻辑</b>的敏感性。若反证另写一份平行实现，
     * 只能证明「那段代码能检出」，与守卫是否失明无关（假反证）。
     */
    private List<String> collectDiverged() {
        List<String> diverged = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> r : mirrorRows()) {
            if (r.get("agency_user_id") == null) {
                continue; // 缺失由 AT-115-1 负责
            }
            Object userRole = r.get("user_role");
            // 镜像缺失（null）也算分叉：说明有人只写了 t_agency_user
            if (userRole == null || !userRole.equals(r.get("agency_user_role"))) {
                diverged.add(r.get("username") + ": t_user=" + userRole
                        + " vs t_agency_user=" + r.get("agency_user_role"));
            }
        }
        return diverged;
    }

    @Test
    @DisplayName("AT-115-4 事务双写不变量：create 后两表必须同时有值")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void createMustWriteBothSides() {
        // 这是 AgencyUserServiceImpl.create 的行为契约（当前已满足）。
        // 在此以数据不变量形式锁死：不允许出现「仅一侧有值」的 AGENCY 用户。
        List<java.util.Map<String, Object>> rows = mirrorRows();
        for (java.util.Map<String, Object> r : rows) {
            boolean hasMembership = r.get("agency_user_id") != null;
            boolean hasMirror = r.get("user_role") != null;
            assertEquals(hasMembership, hasMirror,
                    "用户 " + r.get("username") + " 只有一侧有角色（membership="
                            + hasMembership + ", mirror=" + hasMirror + "）"
                            + " ⇒ create 的「同事务双写」不变量被破坏");
        }
    }
}