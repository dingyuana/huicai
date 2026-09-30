package com.huicai.security;

import com.huicai.agency.user.entity.AgencyUserEntity;
import com.huicai.agency.user.mapper.AgencyUserMapper;
import com.huicai.common.security.EnterpriseMembershipChecker;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AT-102-1 / 1b / 1c —— 企业成员三源并集校验（REQ-2026-129，P102 M4）
 *
 * <p>用<b>真实种子数据</b>跑，而不是 Mock：三个 AGENCY 账号的
 * {@code t_user.enterprise_id} 全为 NULL，能否放行完全取决于
 * {@code t_agency_user_enterprise} 里有没有对应行 —— Mock 夹具天然会
 * 把这些行补齐从而掩盖「真实种子里缺行」的问题（REQ-127 的同款教训）。
 *
 * <p>实测种子（2026-09-30）：
 * <pre>
 *   admin         ent=1     SUPER_ADMIN
 *   accountant01  ent=NULL  AGENCY   → agency_user=2 → 企业 1 ✓
 *   reviewer01    ent=NULL  AGENCY   → agency_user=3 → **无记录** ✗
 *   assistant01   ent=NULL  AGENCY   → agency_user=4 → 企业 1 ✓
 * </pre>
 */
@DisplayName("P102 企业成员校验")
class TenantIsolationSecurityTest extends AbstractMapperTest {

    private static final long ENTERPRISE_A = 1L;

    /** 库里只有企业 1，跨租户场景需自建第二个企业（t_agency_user_enterprise 有外键约束） */
    private Long enterpriseB;

    @org.junit.jupiter.api.BeforeEach
    void createSecondEnterprise() {
        jdbcTemplate.update(
                "INSERT INTO t_enterprise (enterprise_code, enterprise_name, mode, status) "
                        + "VALUES (?, ?, 'SME', 'ACTIVE')",
                "P102-B-" + System.nanoTime(), "P102测试企业B");
        enterpriseB = jdbcTemplate.queryForObject(
                "SELECT max(id) FROM t_enterprise", Long.class);
    }

    @Autowired
    private EnterpriseMembershipChecker checker;

    @Autowired
    private AgencyUserMapper agencyUserMapper;

    @Test
    @DisplayName("AT-102-1 越权切换必须被拒：非成员访问他企业")
    void crossEnterpriseSwitchDenied() {
        // 造一个与任何企业都无关联的普通用户
        Long uid = createSysUser("P102.STRANGER");
        assertFalse(checker.isMember(uid, null, "ENTERPRISE", enterpriseB),
                "与目标企业无任何关联的用户被放行，越权未修复");
    }

    @Test
    @DisplayName("AT-102-1b 代理授权成员可切换（源2）")
    void agencyGrantedMemberAllowed() {
        Long uid = createSysUser("P102.AGENCY");
        Long agencyUserId = createAgencyUser(uid);
        grantEnterprise(agencyUserId, enterpriseB);

        assertTrue(checker.isMember(uid, null, "AGENCY", enterpriseB),
                "已通过代理成员表授权的企业应放行");
        assertFalse(checker.isMember(uid, null, "AGENCY", 999L),
                "未授权的企业应拒绝");
    }

    @Test
    @DisplayName("AT-102-1c SUPER_ADMIN 可切换任意企业（源3）")
    void superAdminAllowedEverywhere() {
        Long uid = createSysUser("P102.SUPER");
        assertTrue(checker.isMember(uid, ENTERPRISE_A, "SUPER_ADMIN", enterpriseB),
                "SUPER_ADMIN 应可全局切换");
    }

    @Test
    @DisplayName("源1 直属企业放行")
    void homeEnterpriseAllowed() {
        Long uid = createSysUser("P102.HOME");
        assertTrue(checker.isMember(uid, ENTERPRISE_A, "ENTERPRISE", ENTERPRISE_A),
                "自己的直属企业当然应放行");
    }

    @Test
    @DisplayName("目标为 null 时不拦截（不因缺头而失败）")
    void nullTargetIsAllowed() {
        Long uid = createSysUser("P102.NULL");
        assertTrue(checker.isMember(uid, ENTERPRISE_A, "ENTERPRISE", null));
    }

    @Test
    @DisplayName("回归：三个 AGENCY 种子账号不得被修复动作锁死")
    void agencySeedAccountsNotLockedOut() {
        String[][] seeds = {
                {"accountant01", "2"},
                {"reviewer01", "3"},
                {"assistant01", "4"},
        };
        for (String[] seed : seeds) {
            Long uid = seedUserId(seed[0]);
            if (uid == null) {
                continue; // 该种子账号在本环境不存在则跳过
            }
            Long agencyUserId = agencyUserIdOf(uid);
            if (agencyUserId == null) {
                continue;
            }
            boolean granted = agencyUserEnterpriseIds(agencyUserId).contains(ENTERPRISE_A);
            boolean allowed = checker.isMember(uid, null, "AGENCY", ENTERPRISE_A);
            assertTrue(granted && allowed,
                    "种子账号 " + seed[0] + " 既无成员表授权又被校验拒绝 ⇒ 修复把它锁死了。"
                            + "需补 t_agency_user_enterprise 种子行（agency_user_id="
                            + seed[1] + " → enterprise_id=" + ENTERPRISE_A + "）");
        }
    }

    @Test
    @DisplayName("反证：agency_user_id 指向的是 t_agency_user.id 而非 t_user.id")
    void agencyUserIdIsNotUserId() {
        Long uid = createSysUser("P102.TWOHOP");
        Long agencyUserId = createAgencyUser(uid);
        // 若实现误把 t_user.id 直接当 agency_user_id 用，此处授权将不生效
        grantEnterprise(agencyUserId, enterpriseB);
        assertTrue(checker.isMember(uid, null, "AGENCY", enterpriseB),
                "两跳链路失效：授权挂在 agency_user_id 上，实现却按 t_user.id 查询");
    }

    // ===== helpers =====

    private Long seedUserId(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM t_user WHERE username = ?", Long.class, username);
    }

    private Long agencyUserIdOf(Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM t_agency_user WHERE user_id = ? AND deleted = 0", Long.class, userId);
    }

    private java.util.List<Long> agencyUserEnterpriseIds(Long agencyUserId) {
        return jdbcTemplate.queryForList(
                "SELECT enterprise_id FROM t_agency_user_enterprise WHERE agency_user_id = ? AND deleted = 0",
                Long.class, agencyUserId);
    }

    private Long createAgencyUser(Long userId) {
        AgencyUserEntity e = new AgencyUserEntity();
        e.setAgencyId(1L);
        e.setUserId(userId);
        e.setAgencyRole("ACCOUNTANT");
        e.setStatus("ACTIVE");
        e.setCreatedAt(java.time.LocalDateTime.now());
        e.setUpdatedAt(java.time.LocalDateTime.now());
        e.setDeleted(0);
        withoutEnterpriseContext(() -> agencyUserMapper.insert(e));
        return e.getId();
    }

    private void grantEnterprise(Long agencyUserId, Long enterpriseId) {
        // assigned_by 为 NOT NULL，须显式赋值（种子行由 admin 分配）
        jdbcTemplate.update(
                "INSERT INTO t_agency_user_enterprise "
                        + "(agency_user_id, enterprise_id, assigned_by, assigned_at, deleted) "
                        + "VALUES (?, ?, 1, NOW(), 0)", agencyUserId, enterpriseId);
    }
}
