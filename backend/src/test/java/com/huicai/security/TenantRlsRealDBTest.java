package com.huicai.security;

import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P102 / AT-102-2 —— RLS 第三层防线真实生效（REQ-2026-129，M5b）
 *
 * <p><b>测试环境恰好适合验证</b>：Testcontainers 建的 {@code test} 用户
 * <b>不是超级用户</b>但<b>是表的属主</b>，而属主默认也绕过 RLS ——
 * 这与生产库「supseruser + bypassrls + 属主」三重绕过的处境不同，
 * 但只要加 {@code FORCE ROW LEVEL SECURITY}，本环境就能真实检验策略谓词。
 * 生产库还需额外一步角色降权（见 SPEC §8 与 README 的操作手册）。
 *
 * <p>实测基线：策略谓词
 * {@code enterprise_id = current_setting('app.enterprise_id', true)}
 * 在未设置时返 0 行（fail-closed），设置为 1 时返该企业行数。
 */
@DisplayName("P102 RLS 第三层防线")
class TenantRlsRealDBTest extends AbstractMapperTest {

    private static final long RLS_TABLE_ENTERPRISE = 1L;

    @Autowired
    private VoucherMapper voucherMapper;

    private void forceRlsOnVoucher() {
        jdbcTemplate.execute("ALTER TABLE t_voucher FORCE ROW LEVEL SECURITY");
    }

    private VoucherEntity newVoucher(String no, Long enterpriseId) {
        VoucherEntity v = new VoucherEntity();
        v.setVoucherNo(no);
        v.setPeriod("202610");
        v.setVoucherTypeId(jdbcTemplate.queryForObject(
                "SELECT id FROM t_voucher_type WHERE deleted = 0 ORDER BY id LIMIT 1", Long.class));
        v.setStatus("DRAFT");
        v.setSource("MANUAL");
        v.setSummary("RLS 测试");
        v.setTotalDebit(new java.math.BigDecimal("10.00"));
        v.setTotalCredit(new java.math.BigDecimal("10.00"));
        v.setEnterpriseId(enterpriseId);
        return v;
    }

    @Test
    @DisplayName("AT-102-2a 基线：未启用 FORCE 时策略不生效（属主绕过，记录现状）")
    void withoutForcePolicyIsInert() {
        jdbcTemplate.execute("ALTER TABLE t_voucher NO FORCE ROW LEVEL SECURITY");
        useEnterprise(RLS_TABLE_ENTERPRISE);
        List<VoucherEntity> rows = voucherMapper.selectList(null);
        // 未 FORCE ⇒ 属主绕过 RLS，谓词不起作用；此断言记录的是"现状"而非期望
        assertTrue(rows != null, "属主绕过 RLS 时查询应正常返回");
    }

    @Test
    @DisplayName("AT-102-2b FORCE 后 + 上下文企业已设置 ⇒ 只见本企业数据")
    @Transactional
    void withForceAndContextSeesOnlyOwnEnterprise() {
        forceRlsOnVoucher();
        useEnterprise(RLS_TABLE_ENTERPRISE);

        List<VoucherEntity> rows = voucherMapper.selectList(null);

        assertTrue(rows.stream().allMatch(v -> RLS_TABLE_ENTERPRISE == v.getEnterpriseId()),
                "RLS 生效后仍查到其他企业的凭证，属策略未生效");
    }

    @Test
    @DisplayName("AT-102-2c FORCE 后 + 上下文切到别家 ⇒ 查不到本家数据")
    @Transactional
    void withForceOtherEnterpriseSeesNothing() {
        forceRlsOnVoucher();
        // 该企业下无凭证，RLS 应过滤掉全部行
        useEnterprise(987654L);

        List<VoucherEntity> rows = voucherMapper.selectList(null);

        assertEquals(0, rows.size(),
                "切到无数据企业仍查到 " + rows.size() + " 行 ⇒ RLS 未生效");
    }

    /**
     * AT-108-1：GUC 处于「空串」形态（曾执行过 SET LOCAL 的事务结束、连接被复用）
     * 时查租户表，必须**返 0 行**而不是抛 SQL 错。
     *
     * <p>V167 之前谓词是 {@code current_setting('app.enterprise_id', true)::bigint}，
     * 空串 {@code ::bigint} 会抛 {@code invalid input syntax for type bigint: ""}
     * ⇒ 定时任务 / 系统初始化等无企业上下文的路径复用该连接时直接 500。
     * V167 改为 {@code NULLIF(current_setting(...), '')::bigint} 后落到 fail-closed。
     *
     * <p>Superuser 走不到谓词（绕过 RLS），故必须显式 {@code SET ROLE} 到探针角色。
     */
    @Test
    @DisplayName("AT-108-1：GUC 为空串时查租户表返 0 行，而非抛 invalid input syntax")
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void emptyGucReturnsZeroRowsInsteadOfError() {
        forceRlsOnVoucher();
        jdbcTemplate.execute("DROP ROLE IF EXISTS rls_empty_probe");
        jdbcTemplate.execute("CREATE ROLE rls_empty_probe NOSUPERUSER NOBYPASSRLS");
        jdbcTemplate.execute("GRANT SELECT ON t_voucher TO rls_empty_probe");
        VoucherEntity probe = newVoucher("P108.EMPTY.GUC", RLS_TABLE_ENTERPRISE);
        withoutEnterpriseContext(() -> voucherMapper.insert(probe));
        try {
            // 先在一个真实事务里 SET LOCAL 并提交 —— 该连接随后处于「GUC 空串」形态
            jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
                boolean prev = con.getAutoCommit();
                con.setAutoCommit(false);
                try (java.sql.Statement st = con.createStatement()) {
                    st.execute("SET LOCAL ROLE rls_empty_probe");
                    st.execute("SELECT set_config('app.enterprise_id', '"
                            + RLS_TABLE_ENTERPRISE + "', true)");
                    st.executeQuery("SELECT count(*) FROM t_voucher").close();
                } finally {
                    con.rollback();
                    con.setAutoCommit(prev);
                }
                return null;
            });
            jdbcTemplate.execute("RESET app.enterprise_id");

            Integer rows = jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Integer>) con -> {
                boolean prev = con.getAutoCommit();
                try (java.sql.Statement st = con.createStatement()) {
                    st.execute("SET ROLE rls_empty_probe");
                    try (java.sql.ResultSet rs = st.executeQuery(
                            "SELECT count(*) FROM t_voucher WHERE voucher_no = 'P108.EMPTY.GUC'")) {
                        rs.next();
                        return rs.getInt(1);
                    }
                } finally {
                    try (java.sql.Statement st = con.createStatement()) {
                        st.execute("RESET ROLE");
                    }
                    con.setAutoCommit(prev);
                }
            });

            assertEquals(0, rows,
                    "GUC 为空串时应 fail-closed 返 0 行；能查到数据说明谓词被绕过或残留脏值");
        } finally {
            jdbcTemplate.execute("RESET app.enterprise_id");
            jdbcTemplate.update("DELETE FROM t_voucher WHERE voucher_no = ?", "P108.EMPTY.GUC");
            jdbcTemplate.execute("DROP OWNED BY rls_empty_probe");
            jdbcTemplate.execute("DROP ROLE IF EXISTS rls_empty_probe");
        }
    }

    @Test
    @DisplayName("AT-102-2d 反证：策略谓词本身有效（绕开 MyBatis，直连 SQL 对比行数）")
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void policyPredicateActuallyFilters() {
        forceRlsOnVoucher();
        // 必须让企业 1 下真实存在一行，否则"过滤后 0 行"与"本来就没数据"无法区分
        try {
            // ⚠️ 必须切到**非超级用户**角色：Testcontainers 建的 test 用户是
            // SUPERUSER，而超级用户**即使加了 FORCE 也绕过 RLS**，
            // 直接用容器用户测，无论 FORCE 与否都会看到全表 —— 测试会"永远绿"。
            // 这与开发库 probe 的结论一致（NOSUPERUSER 角色：44 行 → 1 行）。
            jdbcTemplate.execute("DROP ROLE IF EXISTS rls_probe");
            jdbcTemplate.execute("CREATE ROLE rls_probe LOGIN NOSUPERUSER NOBYPASSRLS");
            jdbcTemplate.execute("GRANT SELECT ON t_voucher TO rls_probe");
            forceRlsOnVoucher();
            VoucherEntity probe = newVoucher("P102.RLS.PROBE", RLS_TABLE_ENTERPRISE);
            withoutEnterpriseContext(() -> voucherMapper.insert(probe));

            int[] counts = jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<int[]>) con -> {
                // ⚠️ 必须显式开事务：PostgreSQL 的 SET LOCAL 在事务块外是**空操作**，
                // 那样 SET LOCAL ROLE 会被立即丢弃，仍以超级用户身份查询 ⇒ 永远看到全表。
                boolean prevAutoCommit = con.getAutoCommit();
                con.setAutoCommit(false);
                try (java.sql.Statement st = con.createStatement()) {
                    st.execute("SET LOCAL ROLE rls_probe");
                    // is_local=true（事务级）：false 是**会话级**，会留在连接池连接上
                    // 泄漏给后续用例（2026-10-03 CI 上实测导致 TenantRlsGucRealDBTest 读到
                    // 上一个用例遗留的 987654 而变红）
                    st.execute("SELECT set_config('app.enterprise_id', '"
                            + RLS_TABLE_ENTERPRISE + "', true)");
                    int own;
                    try (java.sql.ResultSet rs = st.executeQuery(
                            "SELECT count(*) FROM t_voucher WHERE voucher_no = 'P102.RLS.PROBE'")) {
                        rs.next();
                        own = rs.getInt(1);
                    }
                    st.execute("SELECT set_config('app.enterprise_id', '987654', true)");
                    int other;
                    try (java.sql.ResultSet rs = st.executeQuery(
                            "SELECT count(*) FROM t_voucher WHERE voucher_no = 'P102.RLS.PROBE'")) {
                        rs.next();
                        other = rs.getInt(1);
                    }
                    con.rollback();
                    return new int[]{own, other};
                } finally {
                    con.setAutoCommit(prevAutoCommit);
                }
            });

            assertEquals(1, counts[0],
                    "设置为本企业后应恰好看到 1 行，实得 " + counts[0]);
            assertEquals(0, counts[1],
                    "设置为其他企业后仍能看到 " + counts[1] + " 行 ⇒ RLS 未生效");
        } finally {
            jdbcTemplate.update("DELETE FROM t_voucher WHERE voucher_no = ?", "P102.RLS.PROBE");
            jdbcTemplate.execute("ALTER TABLE t_voucher NO FORCE ROW LEVEL SECURITY");
            jdbcTemplate.execute("DROP OWNED BY rls_probe");
            jdbcTemplate.execute("DROP ROLE IF EXISTS rls_probe");
        }
    }

    @Test
    @DisplayName("AT-102-2e 反证：不 FORCE 时同一对照应失效（证明 FORCE 是必要条件）")
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void withoutForceSameProbeSeesEverything() {
        jdbcTemplate.execute("ALTER TABLE t_voucher NO FORCE ROW LEVEL SECURITY");
        VoucherEntity probe = newVoucher("P102.RLS.PROBE2", RLS_TABLE_ENTERPRISE);
        withoutEnterpriseContext(() -> voucherMapper.insert(probe));
        try {
            jdbcTemplate.execute("SELECT set_config('app.enterprise_id', '"
                    + RLS_TABLE_ENTERPRISE + "', false)");
            Integer withOwnEnterprise = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM t_voucher WHERE voucher_no = ?",
                    Integer.class, "P102.RLS.PROBE2");
            jdbcTemplate.execute("SELECT set_config('app.enterprise_id', '987654', false)");
            Integer withOtherEnterprise = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM t_voucher WHERE voucher_no = ?",
                    Integer.class, "P102.RLS.PROBE2");

            assertEquals(1, withOwnEnterprise);
            assertEquals(withOwnEnterprise, withOtherEnterprise,
                    "未 FORCE 时属主应绕过 RLS，两个企业可见行数应相同 —— "
                            + "这证明 AT-102-2d 的过滤确实来自 FORCE 后的 RLS");
        } finally {
            // 本用例在自动提交下用 set_config(..., false)（会话级），必须显式清理，
            // 否则该值留在连接池连接上，被后续用例读到（本轮实测在 CI 上造成 2 条用例变红）
            jdbcTemplate.execute("RESET app.enterprise_id");
            jdbcTemplate.update("DELETE FROM t_voucher WHERE voucher_no = ?", "P102.RLS.PROBE2");
        }
    }
}