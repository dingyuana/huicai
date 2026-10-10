package com.huicai.security;

import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * REQ-2026-135 / V176 —— 「代理端跨客户语义」两表<b>不得</b>开 RLS
 *
 * <h2>为什么要有这个守卫</h2>
 * {@code t_agency_user_enterprise}（会计-客户派工）与 {@code t_service_progress}
 * （代理服务进度）的 {@code enterprise_id} 语义是「<b>被服务的客户</b>」，
 * 不是「本行归属哪个租户」。代理管理员的职责就是跨客户派工。
 * V156 把它们当普通租户表开了 {@code enterprise_policy}
 * （{@code enterprise_id = app.enterprise_id}），实测导致：
 * <ul>
 *   <li>POST /api/v1/agency/assignments → <b>500</b>
 *       （{@code new row violates row-level security policy}）</li>
 *   <li>读路径<b>静默丢行</b>：GUC=1 时查「会计2 的派工」只返 enterprise_id=1，
 *       超管对照返 1,2,3 ⇒ 代理看不到自己已派给其它客户的记录</li>
 * </ul>
 *
 * <h2>🔴 为什么断言必须显式 SET ROLE</h2>
 * Testcontainers 建的 {@code test} 用户是 <b>SUPERUSER</b>，而超级用户
 * <b>即使加了 FORCE 也绕过 RLS</b>（{@link BankReconLogRlsRealDBTest} 同款陷阱，
 * AGENTS §4.5 第 33 条）。本类因此对「行为」断言一律走
 * {@code NOSUPERUSER NOBYPASSRLS} 探针角色；结构断言（pg_class）不需要，
 * 因为那是元数据、与连接角色无关。
 *
 * @see BankReconLogRlsRealDBTest
 */
@DisplayName("REQ-2026-135 RLS：代理端跨客户语义两表必须免 RLS")
class AgencyCrossClientRlsRealDBTest extends AbstractMapperTest {

    private static final String PROBE_ROLE = "r135_probe";
    private static final String[] TABLES = {"t_agency_user_enterprise", "t_service_progress"};

    @Autowired
    private JdbcTemplate jdbc;

    private void ensureProbe() {
        // ⚠️ 必须先 DROP OWNED BY 再 DROP ROLE：上一轮若中途异常退出，
        // 角色上仍挂着 GRANT，裸 DROP ROLE 会报
        // 「cannot be dropped because some objects depend on it」(SQLSTATE 2BP01)，
        // 且该报错会让本类**全部**用例连带失败（实测 3 条一起红）。
        // 顺序反了就会踩：先删角色依赖，再删角色。
        try {
            jdbc.execute("DROP OWNED BY " + PROBE_ROLE);
        } catch (Exception ignored) {
            // 角色不存在时无需清理
        }
        jdbc.execute("DROP ROLE IF EXISTS " + PROBE_ROLE);
        jdbc.execute("CREATE ROLE " + PROBE_ROLE + " NOSUPERUSER NOBYPASSRLS");
        for (String t : TABLES) {
            jdbc.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON " + t + " TO " + PROBE_ROLE);
        }
    }

    private void dropProbe() {
        try {
            jdbc.execute("DROP OWNED BY " + PROBE_ROLE);
        } catch (Exception ignored) {
            // 角色若已不存在则无需清理
        }
        jdbc.execute("DROP ROLE IF EXISTS " + PROBE_ROLE);
    }

    /**
     * 以探针身份跨两个不同 GUC 读同一张表，返回两次的行数。
     *
     * <p>若 RLS 生效，两次行数会不同（谓词按 GUC 过滤）；
     * 若已免 RLS，两次必然相等 —— <b>这就是判据</b>。
     */
    private int[] probeRowCounts(String table, long gucA, long gucB) {
        return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<int[]>) con -> {
            boolean prev = con.getAutoCommit();
            con.setAutoCommit(false);
            try (Statement st = con.createStatement()) {
                st.execute("SET LOCAL ROLE " + PROBE_ROLE);
                int a;
                st.execute("SELECT set_config('app.enterprise_id', '" + gucA + "', true)");
                try (ResultSet rs = st.executeQuery("SELECT count(*) FROM " + table)) {
                    rs.next();
                    a = rs.getInt(1);
                }
                int b;
                st.execute("SELECT set_config('app.enterprise_id', '" + gucB + "', true)");
                try (ResultSet rs = st.executeQuery("SELECT count(*) FROM " + table)) {
                    rs.next();
                    b = rs.getInt(1);
                }
                return new int[]{a, b};
            } finally {
                con.rollback();
                con.setAutoCommit(prev);
            }
        });
    }

    // ───────────────────────── 行为断言 ─────────────────────────

    @Test
    @DisplayName("AT-135-1 t_agency_user_enterprise：换 GUC 不改变可见行数（免 RLS）")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void assignmentTableIsRlsFree() {
        ensureProbe();
        try {
            int[] c = probeRowCounts("t_agency_user_enterprise", 1L, 999999L);
            assertEquals(c[0], c[1],
                    "换 GUC 后可见行数变了（" + c[0] + " vs " + c[1] + "）"
                            + " ⇒ RLS 仍在生效，代理跨客户派工会再次被 with_check 拒（线上 500 复现）");
        } finally {
            dropProbe();
        }
    }

    @Test
    @DisplayName("AT-135-2 t_service_progress：换 GUC 不改变可见行数（免 RLS）")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void serviceProgressTableIsRlsFree() {
        ensureProbe();
        try {
            int[] c = probeRowCounts("t_service_progress", 1L, 999999L);
            assertEquals(c[0], c[1],
                    "换 GUC 后可见行数变了（" + c[0] + " vs " + c[1] + "）⇒ RLS 仍在生效");
        } finally {
            dropProbe();
        }
    }

    @Test
    @DisplayName("AT-135-3 写路径：GUC 与被派企业不同的会话仍能写入（线上 500 的直接回归锁）")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void crossEnterpriseInsertSucceeds() {
        ensureProbe();
        long agencyUserId = createAgencyAccountant();
        // ⚠️ 首版用「不存在的 enterprise_id=999999」来规避外键，方向错了：
        //   外键对企业 2/3 这类真实 id **照常校验**（FK 检查与 RLS 是两套机制，
        //   且被引用表的权限不豁免 FK），实测直接违反 fk_aue_ent。
        //   正确做法是造一条**真实但与 GUC 不同**的企业 —— 那才是本用例要验的场景：
        //   「会话上下文是 A，派工对象是 B」正是代理跨客户派工的定义。
        long otherEnterpriseId = createEnterprise();
        try {
            jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
                boolean prev = con.getAutoCommit();
                con.setAutoCommit(false);
                try (Statement st = con.createStatement()) {
                    st.execute("SET LOCAL ROLE " + PROBE_ROLE);
                    // 刻意让 GUC(1) ≠ 被派企业(otherEnterpriseId)：
                    // 若 RLS 生效，这一句就是线上那个 500。
                    st.execute("SELECT set_config('app.enterprise_id', '1', true)");
                    st.execute("INSERT INTO t_agency_user_enterprise"
                            + " (agency_user_id, enterprise_id, assigned_by, assigned_at, deleted)"
                            + " VALUES (" + agencyUserId + ", " + otherEnterpriseId + ", 1, NOW(), 0)");
                } finally {
                    con.rollback();
                    con.setAutoCommit(prev);
                }
                return null;
            });
        } finally {
            jdbc.update("DELETE FROM t_agency_user_enterprise WHERE agency_user_id = ?", agencyUserId);
            jdbc.update("DELETE FROM t_agency_user WHERE id = ?", agencyUserId);
            jdbc.update("DELETE FROM t_enterprise WHERE enterprise_code = 'R135ENT'");
            jdbc.update("DELETE FROM t_user WHERE username = 'r135_probe_acct'");
            dropProbe();
        }
    }

    /**
     * 造一条<b>与 GUC 不同</b>的真实企业（派工对象）。
     *
     * <p>⚠️ 不能用「不存在的 enterprise_id」来规避外键 —— 实测 FK 照常校验
     * （{@code fk_aue_ent} 违反），因为外键检查与 RLS 是两套独立机制，
     * 且被引用表的授权<b>不豁免</b>对被引用表的 FK 触发器权限。
     * 「会话上下文 = A、派工对象 = B」正是本用例要验的场景，故必须造真实 B。
     */
    private long createEnterprise() {
        return jdbc.queryForObject(
                "INSERT INTO t_enterprise (enterprise_code, enterprise_name, agency_id, status, deleted)"
                        + " VALUES ('R135ENT', 'R135探针企业', 1, 'ACTIVE', 0) RETURNING id", Long.class);
    }

    /**
     * 造一个可用的代理会计，返回 {@code t_agency_user.id}。
     *
     * <p>⚠️ 首版只插了 {@code t_user} 就去查 {@code t_agency_user}，
     * 查不到 → {@code EmptyResultDataAccessException}（expected 1, actual 0）。
     * 两张表都要建 —— {@code t_agency_user.user_id} 是 NOT NULL + FK + UNIQUE，
     * 不能塞 NULL（实测直接违反 not-null 约束）。
     *
     * <p>另：{@code enterprise_id} 那条 FK 指向 {@code t_enterprise}，
     * 故用例刻意用 {@code 999999} 这种不存在的 enterprise_id ——
     * <b>探针角色只被授了 t_agency_user_enterprise 的权限，不含 t_enterprise</b>，
     * 外键检查对该角色不生效，从而能验证「RLS 是否拦写入」这一个变量。
     */
    private long createAgencyAccountant() {
        // t_agency_user.agency_id 有 FK(t_agency)，而迁移后的测试库无代理种子行，
        // 故先按「不存在则建」补一个（§4.3 第 11 条：夹具造主数据一律 find-or-insert，
        // 否则每补一次种子就要炸一批测试）。
        Long agencyId = jdbc.queryForObject("SELECT id FROM t_agency ORDER BY id LIMIT 1", Long.class);
        if (agencyId == null) {
            agencyId = jdbc.queryForObject(
                    "INSERT INTO t_agency (agency_code, agency_name, status, deleted)"
                            + " VALUES ('R135', 'R135探针代理', 'ACTIVE', 0) RETURNING id", Long.class);
        }
        Long userId = jdbc.queryForObject(
                "INSERT INTO t_user (username, password, user_type, agency_id, agency_role,"
                        + " status, deleted) VALUES ('r135_probe_acct', 'x', 'AGENCY', " + agencyId + ","
                        + " 'ACCOUNTANT', 'ACTIVE', 0) RETURNING id", Long.class);
        return jdbc.queryForObject(
                "INSERT INTO t_agency_user (agency_id, user_id, agency_role, status, deleted)"
                        + " VALUES (" + agencyId + ", " + userId + ", 'ACCOUNTANT', 'ACTIVE', 0)"
                        + " RETURNING id", Long.class);
    }

    // ───────────────────────── 结构守卫 ─────────────────────────

    @Test
    @DisplayName("AT-135-4 结构：两表均已 DISABLE RLS 且无 enterprise_policy 残留")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void bothTablesHaveRlsDisabledAndNoPolicy() {
        for (String t : TABLES) {
            Integer rls = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_class WHERE relname = ? AND (relrowsecurity OR relforcerowsecurity)",
                    Integer.class, t);
            assertEquals(0, rls, t + " 仍处于 ENABLE/FORCE RLS 状态 ⇒ V176 未生效或被回退");

            Integer pol = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_policies WHERE schemaname='public' AND tablename=? AND policyname='enterprise_policy'",
                    Integer.class, t);
            assertEquals(0, pol, t + " 仍残留 enterprise_policy ⇒ V176 的 DROP POLICY 未生效");
        }
    }

    /**
     * AT-135-5 反向自检：确认上面的判据<b>不是恒真</b>。
     *
     * <p>做法：临时给一张<b>不该动</b>的租户表（{@code t_subject}）套上同样形状的策略，
     * 验证「换 GUC 行数不同」这一现象确实能被本类的探针观测到 ——
     * 否则 {@link #assignmentTableIsRlsFree} 可能只是<b>什么都没测</b>
     * （§4.5 第 21 条：门禁自身的执行也必须验证）。
     */
    @Test
    @DisplayName("AT-135-5 反证：对一张真租户表施加策略后，本类探针必须能观测到行数变化")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void probeCanDetectRlsWhenItExists() {
        ensureProbe();
        try {
            jdbc.execute("GRANT SELECT ON t_subject TO " + PROBE_ROLE);
            int[] before = probeRowCounts("t_subject", 1L, 999999L);
            jdbc.execute("ALTER TABLE t_subject ENABLE ROW LEVEL SECURITY");
            jdbc.execute("ALTER TABLE t_subject FORCE ROW LEVEL SECURITY");
            jdbc.execute("DROP POLICY IF EXISTS r135_probe_policy ON t_subject");
            jdbc.execute("CREATE POLICY r135_probe_policy ON t_subject FOR ALL "
                    + "USING (enterprise_id = current_setting('app.enterprise_id', true)::bigint)");
            try {
                int[] after = probeRowCounts("t_subject", 1L, 999999L);
                org.junit.jupiter.api.Assertions.assertNotEquals(before[0], after[1],
                        "施加策略后行数竟未变化 ⇒ 探针观测不到 RLS，"
                                + "则 AT-135-1/2 的「行数相等」是假绿，本守卫失效");
            } finally {
                jdbc.execute("DROP POLICY IF EXISTS r135_probe_policy ON t_subject");
                jdbc.execute("ALTER TABLE t_subject NO FORCE ROW LEVEL SECURITY");
                jdbc.execute("ALTER TABLE t_subject DISABLE ROW LEVEL SECURITY");
            }
        } finally {
            dropProbe();
        }
    }
}