package com.huicai.common.test;

import com.huicai.base.system.entity.DeptEntity;
import com.huicai.base.system.mapper.DeptMapper;
import com.huicai.base.system.service.DeptService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P106 批次 1b'：{@code t_dept} 隔离守卫。
 *
 * <p><b>原缺陷</b>：{@code t_dept} 无 {@code enterprise_id}、无 RLS、在 {@code SHARED_TABLES} 内
 * ⇒ 三层防线全空；而 {@code uq_dept_code} 是<b>全局唯一</b>
 * ⇒ <b>两个企业不可能有同名部门编码</b>（新建「财务部」时若另一企业已建过，直接撞唯一键）。
 * {@code DeptServiceImpl} 的 5 个方法（tree/get/create/update/delete）实测<b>不带任何企业维度</b>。
 *
 * <p><b>本守卫锁三件事</b>：
 * <ol>
 *   <li><b>唯一约束按企业分段</b>：A、B 两企业可各建一个「财务部」（修复前的核心危害）；</li>
 *   <li><b>同企业内仍唯一</b>：防「把约束改成完全无约束」的过度修复；</li>
 *   <li><b>读隔离真的生效</b>：A 的部门树不含 B 的部门，
 *       且<b>非超级探针</b>下 RLS 谓词被执行（L2 连接是超级用户，会绕过 RLS，
 *       只用应用层断言无法证明第三层防线存在 —— AGENTS §4.5 第 33 条）。</li>
 * </ol>
 */
@DisplayName("P106 1b' 守卫：t_dept 必须按企业隔离")
class DeptTenantIsolationRealDBTest extends AbstractMapperTest {

    private static final Long ENTERPRISE_A = 990020L;
    private static final Long ENTERPRISE_B = 990021L;

    @Autowired
    private DeptMapper deptMapper;

    @Autowired
    private DeptService deptService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private DeptEntity dept(String code, String name) {
        DeptEntity d = new DeptEntity();
        d.setDeptCode(code);
        d.setName(name);
        d.setSortOrder(1);
        d.setDeleted(0);
        return d;
    }

    /**
     * ①核心价值：两个企业可以各建一个同名部门。
     *
     * <p>修复前实测：第二个企业的「财务部」会撞 {@code uq_dept_code} 全局唯一键。
     */
    @Test
    @DisplayName("两个企业可各建一个同名部门（唯一约束按企业分段）")
    void twoEnterprisesMayHaveSameDeptCode() {
        useEnterprise(ENTERPRISE_A);
        deptMapper.insert(dept("FIN", "财务部"));

        useEnterprise(ENTERPRISE_B);
        deptMapper.insert(dept("FIN", "财务部"));

        Long cntA = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_dept WHERE dept_code = 'FIN'", Long.class);
        assertEquals(2L, cntA,
                "两个企业各有一条 FIN 部门；实际 " + cntA
                        + " ⇒ 若为 1 说明第二企业撞了全局唯一键（未按企业分段）");
    }

    /** ②负向：同一企业内仍必须唯一，防止把约束改成无约束的过度修复。 */
    @Test
    @DisplayName("同一企业内 dept_code 仍唯一")
    void deptCodeStillUniqueWithinSameEnterprise() {
        useEnterprise(ENTERPRISE_A);
        deptMapper.insert(dept("DUPTEST", "研发部"));

        useEnterprise(ENTERPRISE_A);
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DuplicateKeyException.class,
                () -> deptMapper.insert(dept("DUPTEST", "研发部2")),
                "同一企业内重复 dept_code 必须仍被唯一约束拒绝 —— 若能插入说明约束被改成无约束");
    }

    /** ③读隔离：企业 A 的部门树不含企业 B 的部门。 */
    @Test
    @DisplayName("企业 A 的部门树不含企业 B 的部门")
    void deptTreeMustBeScopedToCurrentEnterprise() {
        useEnterprise(ENTERPRISE_A);
        deptMapper.insert(dept("A_ONLY", "A独有部门"));
        useEnterprise(ENTERPRISE_B);
        deptMapper.insert(dept("B_ONLY", "B独有部门"));

        useEnterprise(ENTERPRISE_A);
        List<DeptEntity> tree = deptService.getDeptTree();
        List<String> codes = tree.stream().map(DeptEntity::getDeptCode).toList();
        assertTrue(codes.contains("A_ONLY"), "应看到本企业部门");
        assertTrue(!codes.contains("B_ONLY"),
                "企业 A 不应看到企业 B 的部门，实际读到 " + codes);
    }

    /**
     * ④第三层防线：RLS 谓词在<b>非超级探针</b>下真的被执行。
     *
     * <p>⚠️ 这一条是本守卫与其它断言的关键区别：L2 的连接角色是超级用户
     * （{@code rolsuper=t}/{@code bypassrls=t}）⇒ **RLS 恒被绕过**，
     * 所以「应用层读隔离」与「RLS 存在且有效」是两件必须分别证明的事。
     */
    @Test
    @DisplayName("RLS 生效：非超级探针下企业 A 看不到企业 B 的部门")
    void rowLevelSecurityActuallyBlocksOtherEnterprise() {
        useEnterprise(ENTERPRISE_B);
        deptMapper.insert(dept("RLSPROBE", "B的部门"));


        jdbcTemplate.execute("DROP ROLE IF EXISTS dept_rls_probe");
        jdbcTemplate.execute("CREATE ROLE dept_rls_probe NOSUPERUSER NOBYPASSRLS");
        jdbcTemplate.execute("GRANT SELECT ON t_dept TO dept_rls_probe");

        Integer visibleAsA = jdbcTemplate.execute((ConnectionCallback<Integer>) con -> {
            boolean prev = con.getAutoCommit();
            con.setAutoCommit(false);
            try (Statement st = con.createStatement()) {
                st.execute("SET LOCAL ROLE dept_rls_probe");
                st.execute("SELECT set_config('app.enterprise_id', '" + ENTERPRISE_A + "', true)");
                try (ResultSet rs = st.executeQuery(
                        "SELECT count(*) FROM t_dept WHERE dept_code = 'RLSPROBE'")) {
                    rs.next();
                    return rs.getInt(1);
                }
            } finally {
                con.rollback();
                con.setAutoCommit(prev);
            }
        });

        assertEquals(0, visibleAsA,
                "以非超级探针 + 上下文企业 " + ENTERPRISE_A + " 查询企业 " + ENTERPRISE_B
                        + " 的部门应返 0 行（RLS 谓词读 enterprise_id）；实际可见 " + visibleAsA
                        + " 行 ⇒ t_dept 未开 RLS 或谓词未生效");

        jdbcTemplate.execute("DROP ROLE IF EXISTS dept_rls_probe");
    }

    /** ⑤守卫前提：L2 连接角色是超级用户，故本类断言必须走探针。 */
    @Test
    @DisplayName("守卫前提：L2 连接角色是超级用户（故 ④ 才需要 SET LOCAL ROLE）")
    void l2RoleIsSuperuserSoRlsNeedsProbe() {
        Boolean superuser = jdbcTemplate.queryForObject(
                "SELECT rolsuper FROM pg_roles WHERE rolname = current_user", Boolean.class);
        assertTrue(Boolean.TRUE.equals(superuser),
                "本类第 ④ 条断言建立在「L2 连接是超级用户、RLS 被绕过」这一前提上；"
                        + "若将来改用非超级角色，④ 的探针写法需同步复核。当前 rolsuper=" + superuser);
    }
}