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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P112 / REQ-2026-112 —— RLS 第三层缺口闭合（{@code t_bank_reconciliation_log}）
 *
 * <p><b>背景</b>：M5b-V2 已把应用切到非超管 {@code huicai_app}，71 张表 RLS 生效，
 * 但本表因未开策略而落在兜底之外。实测补齐。</p>
 *
 * <p><b>为什么每个断言都必须显式 SET ROLE</b>：Testcontainers 建的 {@code test}
 * 用户是 SUPERUSER，而超级用户<b>即使加了 FORCE 也绕过 RLS</b>；直接用容器用户测，
 * 无论策略与否都会看到全表 —— 测试会「永远绿」（§4.5 第 33 条）。故本类全部走
 * {@code NOSUPERUSER NOBYPASSRLS} 探针角色。</p>
 */
@DisplayName("SPEC-P112 RLS 兜底：银行对账日志")
class BankReconLogRlsRealDBTest extends AbstractMapperTest {

    private static final String PROBE_ROLE = "p112_recon_probe";
    private static final String TBL = "t_bank_reconciliation_log";
    private static final long OWN_ENT = 1L;
    private static final long OTHER_ENT = 998877L;

    @Autowired
    private JdbcTemplate jdbc;

    /** 建探针角色并授权（探针必须 NOSUPERUSER，否则 RLS 被绕过）。 */
    private void ensureProbe() {
        jdbc.execute("DROP ROLE IF EXISTS " + PROBE_ROLE);
        jdbc.execute("CREATE ROLE " + PROBE_ROLE + " NOSUPERUSER NOBYPASSRLS");
        jdbc.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON " + TBL + " TO " + PROBE_ROLE);
    }

    private void dropProbe() {
        try {
            jdbc.execute("DROP OWNED BY " + PROBE_ROLE);
        } catch (Exception ignored) {
            // 角色若已不存在则无需清理
        }
        jdbc.execute("DROP ROLE IF EXISTS " + PROBE_ROLE);
    }

    /** 在显式事务里以探针身份执行 SQL —— PostgreSQL 的 SET LOCAL 在事务块外是空操作。 */
    private int[] probeCounts(long probeStatementId) {
        return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<int[]>) con -> {
            boolean prev = con.getAutoCommit();
            con.setAutoCommit(false);
            try (Statement st = con.createStatement()) {
                st.execute("SET LOCAL ROLE " + PROBE_ROLE);
                int own;
                st.execute("SELECT set_config('app.enterprise_id', '" + OWN_ENT + "', true)");
                try (ResultSet rs = st.executeQuery(
                        "SELECT count(*) FROM " + TBL + " WHERE statement_id = " + probeStatementId)) {
                    rs.next();
                    own = rs.getInt(1);
                }
                int other;
                st.execute("SELECT set_config('app.enterprise_id', '" + OTHER_ENT + "', true)");
                try (ResultSet rs = st.executeQuery(
                        "SELECT count(*) FROM " + TBL + " WHERE statement_id = " + probeStatementId)) {
                    rs.next();
                    other = rs.getInt(1);
                }
                int none;
                st.execute("SELECT set_config('app.enterprise_id', '', true)");
                try (ResultSet rs = st.executeQuery(
                        "SELECT count(*) FROM " + TBL + " WHERE statement_id = " + probeStatementId)) {
                    rs.next();
                    none = rs.getInt(1);
                }
                return new int[]{own, other, none};
            } finally {
                con.rollback();
                con.setAutoCommit(prev);
            }
        });
    }

    /** 造一条属于 OWN_ENT 的日志行（statement_id 无 FK，可用合成值）。 */
    private long seedRow(long statementId) {
        return jdbc.queryForObject("INSERT INTO " + TBL
                        + " (statement_id, action, status_after, operator, enterprise_id)"
                        + " VALUES (?, 'CONFIRM', 'MATCHED', 'p112', ?::bigint) RETURNING id",
                Long.class, statementId, OWN_ENT);
    }

    // ───────────────────────── AT-112-1~6：行为 ─────────────────────────

    @Test
    @DisplayName("AT-112-1/2/3 策略生效：本企业可见 / 跨企业 0 行 / 无 GUC 0 行")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void policyFiltersByEnterprise() {
        ensureProbe();
        long sid = 998877001L;
        seedRow(sid);
        try {
            int[] c = probeCounts(sid);
            assertEquals(1, c[0], "本企业应可见 1 行，实际 " + c[0]);
            assertEquals(0, c[1], "跨企业应 0 行，实际 " + c[1] + " ⇒ RLS 未生效");
            assertEquals(0, c[2], "GUC 空串应 fail-closed 0 行，实际 " + c[2]);
        } finally {
            jdbc.update("DELETE FROM " + TBL + " WHERE statement_id = ?", sid);
            dropProbe();
        }
    }

    @Test
    @DisplayName("AT-112-4 GUC 空串不抛 invalid input syntax（V167 硬化谓词）")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void emptyGucDoesNotThrow() {
        ensureProbe();
        // 策略谓词必须用 NULLIF(current_setting(...),'')::bigint；
        // 若被写成 current_setting(...)::bigint，本用例会抛 SQL 错而失败。
        int[] c = probeCounts(998877002L);
        // 不依赖数据，只要求「不抛异常」
        assertTrue(c.length == 3);
        dropProbe();
    }

    @Test
    @DisplayName("AT-112-5 反证：超管绕过 RLS（同一 SQL 走 test 角色看到全表）")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void superuserBypassesRls() {
        long sid = 998877003L;
        seedRow(sid);
        try {
            // 以容器用户（超级用户）身份、跨企业 GUC 直接查 —— 应不被过滤
            jdbc.execute("BEGIN");
            Integer n = jdbc.queryForObject(
                    "SELECT count(*) FROM " + TBL + " WHERE statement_id = " + sid, Integer.class);
            jdbc.execute("ROLLBACK");
            assertEquals(1, n, "超管应绕过 RLS 看到该行；若看不到说明连接角色已非超管，需重新评估本用例前提");
        } finally {
            jdbc.update("DELETE FROM " + TBL + " WHERE statement_id = ?", sid);
        }
    }

    @Test
    @DisplayName("AT-112-6 写路径：本企业写成功，跨企业读不到")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void writeVisibleOnlyToOwnEnterprise() {
        ensureProbe();
        long sid = 998877004L;
        try {
            int[] c = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<int[]>) con -> {
                boolean prev = con.getAutoCommit();
                con.setAutoCommit(false);
                try (Statement st = con.createStatement()) {
                    st.execute("SET LOCAL ROLE " + PROBE_ROLE);
                    st.execute("SELECT set_config('app.enterprise_id', '" + OWN_ENT + "', true)");
                    st.execute("INSERT INTO " + TBL
                            + " (statement_id, action, status_after, operator, enterprise_id)"
                            + " VALUES (" + sid + ", 'REJECT', 'UNMATCHED', 'p112w', " + OWN_ENT + ")");
                    int own;
                    try (ResultSet rs = st.executeQuery(
                            "SELECT count(*) FROM " + TBL + " WHERE statement_id = " + sid)) {
                        rs.next();
                        own = rs.getInt(1);
                    }
                    st.execute("SELECT set_config('app.enterprise_id', '" + OTHER_ENT + "', true)");
                    int other;
                    try (ResultSet rs = st.executeQuery(
                            "SELECT count(*) FROM " + TBL + " WHERE statement_id = " + sid)) {
                        rs.next();
                        other = rs.getInt(1);
                    }
                    return new int[]{own, other};
                } finally {
                    con.rollback();
                    con.setAutoCommit(prev);
                }
            });
            assertEquals(1, c[0], "本企业写入后应可见");
            assertEquals(0, c[1], "跨企业应读不到，实际 " + c[1]);
        } finally {
            jdbc.update("DELETE FROM " + TBL + " WHERE statement_id = ?", sid);
            dropProbe();
        }
    }

    // ───────────────────────── AT-112-7：结构守卫 ─────────────────────────

    @Test
    @DisplayName("AT-112-7 结构：本表已 ENABLE+FORCE RLS 且策略名为 enterprise_policy")
    void tableHasRlsEnabledAndPolicy() {
        Integer rls = jdbc.queryForObject(
                "SELECT count(*) FROM pg_class WHERE relname = ? AND relrowsecurity AND relforcerowsecurity",
                Integer.class, TBL);
        assertEquals(1, rls, TBL + " 应同时 ENABLE + FORCE ROW LEVEL SECURITY");

        Integer pol = jdbc.queryForObject(
                "SELECT count(*) FROM pg_policies WHERE schemaname='public' AND tablename=? AND policyname='enterprise_policy'",
                Integer.class, TBL);
        assertEquals(1, pol, TBL + " 应有 1 条 enterprise_policy 策略");
    }
}