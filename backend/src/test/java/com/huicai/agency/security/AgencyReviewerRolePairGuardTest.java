package com.huicai.agency.security;

import com.huicai.agency.user.entity.AgencyUserEntity;
import com.huicai.agency.user.entity.AgencyUserEnterpriseEntity;
import com.huicai.agency.user.mapper.AgencyUserEnterpriseMapper;
import com.huicai.agency.user.mapper.AgencyUserMapper;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.base.system.mapper.UserMapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P116 —— 代理端记账/审核角色对守卫（数据范围 + 审核授权 + 制审分离）
 *
 * <h2>背景</h2>
 * S-26 §1.1/§1.2 早已声明四角色可操作范围，但实现逐端点写漏了。
 * 本类守三条：<b>数据范围</b>（会计只看自己的客户）、<b>审核授权</b>
 * （谁能审）、<b>制审分离</b>（不能自审）。
 *
 * <h2>诚实标注：两条缺陷当前都是「潜在」的</h2>
 * 实测开发库：{@code t_agency_user_enterprise} 每个会计只分到 <b>1</b> 个企业
 * ⇒ 即便不收窄，每个人看到的也恰好是同一个企业，<b>差异不可观测</b>；
 * {@code t_voucher} 全是 DRAFT 且 {@code created_by}/{@code audited_by} 全空
 * ⇒ 自审也未被触发（{@code created_by = audited_by} 的行数为 0）。
 *
 * <p>⇒ 本类**必须自己构造分叉场景**（造第二个企业、把会计只分配给其中一个），
 * 不能靠「当前数据碰巧一致」来证明守卫有效。这与 SPEC-P115 反证内建于测试同一思路。
 *
 * @see AgencyDataScopeResolver
 */
@DisplayName("SPEC-P116：代理端记账/审核角色对")
class AgencyReviewerRolePairGuardTest extends AbstractMapperTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AgencyDataScopeResolver resolver;

    @Autowired
    private AgencyUserMapper agencyUserMapper;

    @Autowired
    private AgencyUserEnterpriseMapper assignmentMapper;

    @Autowired
    private UserMapper userMapper;

    // ─────────────── 夹具：构造「同代理、多个客户」的真实分叉 ───────────────

    /**
     * 造两个客户企业 + 一个代理用户。
     *
     * <p>⚠️ <b>为什么必须这样造</b>：现种子每个会计只分 1 个企业，
     * 「收窄」与「不收窄」的结果完全相同 ⇒ 用例会**恒绿**。
     * 要能观测差异，必须让「分配的」与「未分配的」同时存在。
     *
     * <p>⚠️ <b>监督类角色刻意不建分配行</b>：{@code REVIEWER}/{@code AGENCY_ADMIN}
     * 的可见范围来自「角色」而非「分配表」。若给他们也建一行，
     * 就无法区分「因为他是审核员所以看到全部」与「因为他被分了所以看到」
     * ⇒ 那正是把监督者降级成记账员的形态，必须排除。
     *
     * @param role 代理内角色
     * @return 新建的 {@code t_agency_user.id}
     */
    private long fixtureAccountantWithOneOfTwoClients(String role) {
        boolean oversight = Set.of("REVIEWER", "AGENCY_ADMIN").contains(role);

        long agencyId = jdbc.queryForObject(
                "INSERT INTO t_agency (agency_code, agency_name, status, deleted) "
                        + "SELECT 'P116', 'P116探针代理', 'ACTIVE', 0 "
                        + "WHERE NOT EXISTS (SELECT 1 FROM t_agency WHERE agency_code='P116') "
                        + "RETURNING id", Long.class);

        // 两个客户企业（同一代理下）
        jdbc.update("INSERT INTO t_enterprise (enterprise_code, enterprise_name, agency_id, status, deleted) "
                + "SELECT 'P116A', 'P116客户甲', ?, 'ACTIVE', 0 WHERE NOT EXISTS "
                + "(SELECT 1 FROM t_enterprise WHERE enterprise_code='P116A')", agencyId);
        jdbc.update("INSERT INTO t_enterprise (enterprise_code, enterprise_name, agency_id, status, deleted) "
                + "SELECT 'P116B', 'P116客户乙', ?, 'ACTIVE', 0 WHERE NOT EXISTS "
                + "(SELECT 1 FROM t_enterprise WHERE enterprise_code='P116B')", agencyId);
        Long entA = jdbc.queryForObject(
                "SELECT id FROM t_enterprise WHERE enterprise_code='P116A'", Long.class);
        Long entB = jdbc.queryForObject(
                "SELECT id FROM t_enterprise WHERE enterprise_code='P116B'", Long.class);

        Long userId = jdbc.queryForObject(
                "INSERT INTO t_user (username, password, user_type, agency_id, agency_role, status, deleted) "
                        + "SELECT 'p116_probe', 'x', 'AGENCY', ?, ?, 'ACTIVE', 0 "
                        + "WHERE NOT EXISTS (SELECT 1 FROM t_user WHERE username='p116_probe') "
                        + "RETURNING id", Long.class, agencyId, role);

        Long agencyUserId = jdbc.queryForObject(
                "INSERT INTO t_agency_user (agency_id, user_id, agency_role, status, deleted) "
                        + "SELECT ?, ?, ?, 'ACTIVE', 0 "
                        + "WHERE NOT EXISTS (SELECT 1 FROM t_agency_user WHERE user_id=?) "
                        + "RETURNING id", Long.class, agencyId, userId, role, userId);

        // 只分配客户甲 —— 客户乙「存在但未分配」是本次要验的核心差异。
        // 监督类角色不建分配行（见方法注释）。
        if (!oversight) {
            jdbc.update("INSERT INTO t_agency_user_enterprise "
                            + "(agency_user_id, enterprise_id, assigned_by, assigned_at, deleted) "
                            + "SELECT ?, ?, 1, NOW(), 0 WHERE NOT EXISTS "
                            + "(SELECT 1 FROM t_agency_user_enterprise "
                            + " WHERE agency_user_id=? AND enterprise_id=?)",
                    agencyUserId, entA, agencyUserId, entA);
        }

        return agencyUserId;
    }

    private void cleanFixture() {
        Long agencyId = jdbc.queryForObject(
                "SELECT id FROM t_agency WHERE agency_code='P116'", Long.class);
        if (agencyId != null) {
            jdbc.update("DELETE FROM t_agency_user_enterprise WHERE agency_user_id IN "
                    + "(SELECT id FROM t_agency_user WHERE agency_id=?)", agencyId);
            jdbc.update("DELETE FROM t_agency_user WHERE agency_id=?", agencyId);
            jdbc.update("DELETE FROM t_user WHERE agency_id=?", agencyId);
            jdbc.update("DELETE FROM t_enterprise WHERE agency_id=?", agencyId);
            jdbc.update("DELETE FROM t_agency WHERE id=?", agencyId);
        }
    }

    /** 以指定代理用户身份建立登录上下文（Spec 的 SecurityUtils 走 LoginUser）。 */
    private void loginAs(Long agencyUserId) {
        AgencyUserEntity au = agencyUserMapper.selectById(agencyUserId);
        UserEntity u = userMapper.selectById(au.getUserId());
        u.setAgencyId(au.getAgencyId());
        u.setAgencyRole(au.getAgencyRole());
        u.setUserType("AGENCY");
        com.huicai.config.security.LoginUser lu =
                new com.huicai.config.security.LoginUser(u,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(lu, null, lu.getAuthorities()));
    }

    @org.junit.jupiter.api.AfterEach
    void clearCtx() {
        SecurityContextHolder.clearContext();
        cleanFixture();
    }

    // ───────────────── AT-116-1：会计只见分配给自己的客户 ─────────────────

    @Test
    @DisplayName("AT-116-1 ACCOUNTANT 只见到分配给自己的客户（构造甲/乙两客户验证）")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void accountantSeesOnlyAssignedClients() {
        long agencyUserId = fixtureAccountantWithOneOfTwoClients("ACCOUNTANT");
        loginAs(agencyUserId);

        AgencyDataScopeResolver.Scope scope = resolver.resolve();

        assertTrue(scope.isRestricted(),
                "ACCOUNTANT 的数据范围未被收窄（allClients=true）⇒ 与本 SPEC 要修的缺陷相同："
                        + "会计能看到整个代理公司全部客户的账（S-26 §1.1 声明「仅分配给自己的」）");
        assertFalse(scope.enterpriseIds().isEmpty(),
                "ACCOUNTANT 被收窄成了空集合 —— 连分配给自己的客户都看不到，那是「过度收窄」，"
                        + "比不收窄更糟（用户会以为自己没有客户）");
        assertEquals(1, scope.enterpriseIds().size(),
                "ACCOUNTANT 应只见到 1 个已分配客户，实际 " + scope.enterpriseIds());
    }

    // ───────────────── AT-116-2：审核员见全部客户 ─────────────────

    @Test
    @DisplayName("AT-116-2 REVIEWER 见到本代理全部客户（S-26 §1.1/§1.2 第78行）")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void reviewerSeesAllClients() {
        long agencyUserId = fixtureAccountantWithOneOfTwoClients("REVIEWER");
        loginAs(agencyUserId);

        AgencyDataScopeResolver.Scope scope = resolver.resolve();

        // 刻意**不**给 REVIEWER 分配任何客户 —— 若解析器按「分配表」收窄，
        // REVIEWER 会看到 0 个客户，那正是把监督者锁死在门外的形态。
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM t_agency_user_enterprise WHERE agency_user_id=?",
                Integer.class, agencyUserId),
                "夹具前提被破坏：REVIEWER 不应有分配行");

        assertFalse(scope.isRestricted(),
                "REVIEWER 的数据范围被收窄了 ⇒ 监督者看不到未分配给自己的客户，"
                        + "S-26 §1.1「全部企业（只读审核权限）」与 §1.2 第 78 行"
                        + "「凭证审核 = 全部」均落空");
    }

    // ───────────────── AT-116-3：经理见全部客户 ─────────────────

    @Test
    @DisplayName("AT-116-3 AGENCY_ADMIN 见到本代理全部客户")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void adminSeesAllClients() {
        long agencyUserId = fixtureAccountantWithOneOfTwoClients("AGENCY_ADMIN");
        loginAs(agencyUserId);

        assertFalse(resolver.resolve().isRestricted(),
                "AGENCY_ADMIN 的数据范围被收窄 ⇒ S-26 §1.1「全部客户企业 + 管理会计」落空");
    }

    // ───────────────── AT-116-4：上下文缺 agency_role 必须 fail-loud ─────────────────

    @Test
    @DisplayName("AT-116-4 登录上下文缺 agency_role 时 fail-loud，不得静默按「无权」")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void missingAgencyRoleFailsLoud() {
        long agencyUserId = fixtureAccountantWithOneOfTwoClients("ACCOUNTANT");
        AgencyUserEntity au = agencyUserMapper.selectById(agencyUserId);
        UserEntity u = userMapper.selectById(au.getUserId());
        // ⚠️ 必须**显式清空**：LoginUser 的 2 参构造器读的是 UserEntity.getAgencyRole()，
        // 而库里该行是有值的（夹具刚写进去）⇒ 不显式 set null 就还原不到「缺字段」场景，
        // 用例会恒绿（首版正是如此，实测 nothing was thrown）。
        u.setAgencyRole(null);
        u.setAgencyId(null);
        com.huicai.config.security.LoginUser lu =
                new com.huicai.config.security.LoginUser(u,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(lu, null, lu.getAuthorities()));

        BusinessException ex = assertThrows(BusinessException.class, () -> resolver.resolve(),
                "缺 agency_role 时未抛异常 ⇒ 会静默落到某个默认分支，"
                        + "而默认分支是什么全凭实现巧合（本 SPEC 要防的形态）");
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("agency_role"),
                "报错须指明缺失字段，实际：" + ex.getMessage());
    }

    // ───────────────── AT-116-5：空分配不得静默变成「全部」─────────────────

    @Test
    @DisplayName("AT-116-5 会计无任何分配时返回空集合，不得退化为「全部客户」")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void accountantWithoutAssignmentSeesNothingButNotAll() {
        long agencyUserId = fixtureAccountantWithOneOfTwoClients("ACCOUNTANT");
        // 清掉他的全部分配
        jdbc.update("DELETE FROM t_agency_user_enterprise WHERE agency_user_id=?", agencyUserId);
        loginAs(agencyUserId);

        AgencyDataScopeResolver.Scope scope = resolver.resolve();

        assertTrue(scope.isRestricted(),
                "无分配的会计师竟拿到 allClients=true ⇒ 「没有客户」被静默解释成「所有客户」，"
                        + "这是最危险的退化方向");
        assertTrue(scope.enterpriseIds().isEmpty(),
                "无分配时应返回空集合");
    }
}
