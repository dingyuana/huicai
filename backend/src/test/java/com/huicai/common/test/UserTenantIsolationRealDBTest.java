package com.huicai.common.test;

import com.huicai.base.system.entity.UserEntity;
import com.huicai.base.system.service.UserService;
import com.huicai.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 守卫：用户管理端点**不得跨企业**读写 {@code t_user}。
 *
 * <p><b>缺陷（2026-10-07 真库实测）</b>：
 * {@code UserServiceImpl} 全部方法对 {@code t_user} 的读写**都没有企业维度**：
 * <ul>
 *   <li>{@code pageUser} wrapper 只有 {@code deleted} / {@code keyword} / {@code deptId} / {@code status}</li>
 *   <li>{@code getById} / {@code update} / {@code updateStatus} / {@code resetPassword} / {@code assignRoles}
 *       / {@code delete} 全部直接按 id 操作</li>
 * </ul>
 * 而 {@code t_user} 在 {@code EnterpriseDataPermissionInterceptor.SHARED_TABLES} 内 ⇒
 * 第二层防线**不注入**条件；DB 层实测 {@code relrowsecurity=false}、{@code policies=0}、
 * 非超级探针也照样看到两家数据 ⇒ <b>三层防线全空</b>。
 * 实测输出：{@code ctx=990007} 调用 {@code pageUser} 返回了企业 1 的用户
 * （{@code -> ent=1 username=probe_e1_a}）。
 *
 * <p><b>为什么不能简单把 t_user 移出 SHARED_TABLES</b>：
 * 登录链路（{@code AuthController} → {@code UserDetailsServiceImpl} → {@code selectByUsername}）
 * 必须在<b>尚无企业上下文</b>时按用户名查人 —— 若注入 {@code enterprise_id}，
 * 登录时 ctx 为 null，谓词变成 {@code = NULL} 恒不成立 ⇒ 所有人无法登录。
 * 而代理端（{@code AgencyUserServiceImpl}）需要跨客户企业读本代理的用户，
 * 其 {@code enterprise_id} 语义是「归属企业」而非当前上下文。
 * ⇒ 本守卫锁定的是**企业用户管理路径**的隔离，
 * 登录/成员校验/代理平台的豁免理由见 SPEC §0.1.2 B 类。
 *
 * <p><b>⚠️ 本类全部断言走真实 Service 回路</b>（不是裸 mapper）：
 * 缺陷就在 Service 层漏了过滤，直接调 mapper 会让用例「通过」而端点仍泄漏。
 */
@DisplayName("P0 守卫：用户管理不得跨企业泄漏 t_user")
class UserTenantIsolationRealDBTest extends AbstractMapperTest {

    private static final Long ENTERPRISE_A = 990010L;
    private static final Long ENTERPRISE_B = 990011L;
    private static final String PREFIX = "9999.P0.USER.";

    @Autowired
    private UserService userService;

    private Long createUserIn(Long enterpriseId, String tag) {
        UserEntity u = new UserEntity();
        u.setUsername(PREFIX + tag + "-" + System.nanoTime());
        u.setPassword("encoded");
        u.setRealName("真实姓名-" + tag);
        u.setEmail("mail-" + tag + "@example.com");
        u.setPhone("13800000000");
        u.setStatus("ACTIVE");
        u.setUserType("ENTERPRISE");
        u.setDeleted(0);
        // 与生产一致：t_user 不继承 BaseEntity ⇒ insertFill 不触发，企业号必须显式写入
        userMapper.insert(u);
        jdbcTemplate.update("UPDATE t_user SET enterprise_id = ? WHERE id = ?", enterpriseId, u.getId());
        return u.getId();
    }

    /**
     * 正向：企业 A 只能列出本企业的用户。
     *
     * <p>修复前实测：A 能看到 B 的用户。
     */
    @Test
    @DisplayName("企业 A 的用户列表只含本企业用户（不得含别家企业）")
    void pageUserMustBeScopedToCurrentEnterprise() {
        Long idA = createUserIn(ENTERPRISE_A, "A");
        Long idB = createUserIn(ENTERPRISE_B, "B");

        useEnterprise(ENTERPRISE_A);
        var page = userService.pageUser(1, 50, PREFIX, null, null);

        List<Long> ids = page.getRecords().stream().map(UserEntity::getId).toList();
        assertTrue(ids.contains(idA), "企业 A 应能看到自己的用户");
        assertTrue(!ids.contains(idB),
                "企业 A 不应看到企业 B 的用户 —— 实测泄漏过（返回 " + page.getTotal() + " 条含跨企业记录）");
        page.getRecords().forEach(u ->
                assertEquals(ENTERPRISE_A, u.getEnterpriseId(),
                        "列表内每条记录的 enterprise_id 都必须是当前企业"));
    }

    /** 负向：按 id 读别家企业用户必须被拒。 */
    @Test
    @DisplayName("企业 A 读取企业 B 的用户详情必须抛异常")
    void getByIdOfOtherEnterpriseMustThrow() {
        Long idB = createUserIn(ENTERPRISE_B, "B");

        useEnterprise(ENTERPRISE_A);
        assertThrows(BusinessException.class, () -> userService.getById(idB),
                "跨企业读用户详情必须拒绝 —— 否则可按猜测 id 遍历全部企业 PII");
    }

    /** 负向：按 id 改别家企业用户必须被拒，且**数据不得被改动**。 */
    @Test
    @DisplayName("企业 A 修改企业 B 的用户状态必须抛异常且不改数据")
    void updateStatusOfOtherEnterpriseMustThrowAndNotModify() {
        Long idB = createUserIn(ENTERPRISE_B, "B");
        String before = jdbcTemplate.queryForObject(
                "SELECT status FROM t_user WHERE id = ?", String.class, idB);

        useEnterprise(ENTERPRISE_A);
        assertThrows(BusinessException.class, () -> userService.updateStatus(idB, "INACTIVE"));

        String after = jdbcTemplate.queryForObject(
                "SELECT status FROM t_user WHERE id = ?", String.class, idB);
        assertEquals(before, after, "越权写入被拒后，数据必须保持原样（负向断言）");
    }

    /** 负向：无企业上下文时必须 fail-closed，而不是返回全量用户。 */
    @Test
    @DisplayName("无企业上下文时用户列表抛异常（fail-closed，不得返回全表）")
    void pageUserWithoutEnterpriseContextMustThrow() {
        createUserIn(ENTERPRISE_A, "A");

        withoutEnterpriseContext(() -> {
            assertThrows(BusinessException.class, () -> userService.pageUser(1, 50, PREFIX, null, null),
                    "无企业上下文时不得返回全量用户 —— 这是本缺陷最危险的一档（定时任务/忘记设上下文即全量泄漏）");
        });
    }

    /**
     * 新建用户必须落到当前企业。
     *
     * <p>为什么必须锁这条：{@code UserSaveDTO} 刻意<b>不含</b> {@code enterpriseId}，
     * 且 {@code UserEntity} 不继承 {@code BaseEntity} ⇒ {@code insertFill} 不触发
     * ⇒ 新用户的 {@code enterprise_id} 会是 {@code NULL}。
     * 一旦列表按企业过滤，这些用户将<b>永远不可见</b>（自己建的管理员看不到自己建的人），
     * 同时它们也等于「不属于任何企业」的游离数据 —— 与预付款 1a-1 同型的静默错位。
     */
    @Test
    @DisplayName("新建用户必须落当前企业（不得为 NULL）")
    void createdUserMustBelongToCurrentEnterprise() {
        useEnterprise(ENTERPRISE_A);

        UserEntity u = new UserEntity();
        u.setUsername(PREFIX + "NEW-" + System.nanoTime());
        u.setPassword("plain");
        u.setRealName("新建用户");
        u.setStatus("ACTIVE");
        u.setUserType("ENTERPRISE");
        u.setDeleted(0);
        userService.create(u);

        assertNotNull(u.getId(), "应返回新建用户 id");
        Long enterpriseId = jdbcTemplate.queryForObject(
                "SELECT enterprise_id FROM t_user WHERE id = ?", Long.class, u.getId());
        assertEquals(ENTERPRISE_A, enterpriseId,
                "新建用户必须归属当前企业 " + ENTERPRISE_A + "，实际 " + enterpriseId
                        + "（为 null 说明未按上下文落库，列表按企业过滤后将永远看不到它）");
    }

    /** 正向对照：本企业用户可正常读写，确保修复不是「一律拒绝」。 */
    @Test
    @DisplayName("本企业用户可正常读写（修复不得变成一律拒绝）")
    void ownEnterpriseUserRemainsAccessible() {
        Long idA = createUserIn(ENTERPRISE_A, "A");

        useEnterprise(ENTERPRISE_A);
        UserEntity found = userService.getById(idA);
        assertNotNull(found, "本企业用户应可读");
        assertEquals("真实姓名-A", found.getRealName(), "读到的是本企业用户的真实姓名");

        userService.updateStatus(idA, "INACTIVE");
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM t_user WHERE id = ?", String.class, idA);
        assertEquals("INACTIVE", status, "本企业用户应可改");

        Integer notDeleted = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_user WHERE id = ? AND deleted = 0", Integer.class, idA);
        assertEquals(1, notDeleted, "改状态不应影响逻辑删除标记");
    }
}