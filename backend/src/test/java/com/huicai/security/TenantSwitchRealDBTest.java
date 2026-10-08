package com.huicai.security;

import com.huicai.agency.user.entity.AgencyUserEntity;
import com.huicai.agency.user.mapper.AgencyUserMapper;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.base.system.mapper.UserMapper;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.config.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P106 / REQ-2026-133 / <b>批次 2</b>：切换鉴权与跨企业隔离的真库回归锁
 * （AT-106-6 / AT-106-7 / AT-106-8 + 跨企业写拒绝）
 *
 * <p><b>与批次 1a 的分工</b>：1a 验的是「写入路径是否落到正确的企业」，
 * 本类验的是「<b>上下文是否被正确设置</b>」—— 即 {@code JwtAuthenticationFilter}
 * 拿到 {@code X-Enterprise-Id} 后是否放行、是否改写 {@code EnterpriseContextHolder}、
 * 拒绝时是否留痕。三者任一环节断掉，1a 的断言都仍会全绿（因为 1a 直接调 Service，
 * 自己 {@code useEnterprise(...)} 设置上下文，根本不经过过滤器）。
 *
 * <p><b>为什么用真 JWT 而不 Mock</b>：{@code TenantIsolationHttpTest} 只验「无凭证 → 401」，
 * 因为它无法回答「带合法凭证时上下文如何变」。本类用真实 {@link JwtProvider} 签发 token，
 * 让 {@code JwtAuthenticationFilter} 的真实逻辑（含 Redis 黑名单校验、成员三源并集）
 * 真正跑起来。Mock 掉 {@code JwtProvider} 就等于把被测对象一起 Mock 掉了。
 *
 * <p><b>观察手段：{@code /api/v1/enterprise/current-period}</b>
 * <br>该端点直接以 {@code EnterpriseContextHolder.get()} 为入参查 {@code t_enterprise}
 * （共享表，不被数据权限拦截器注入条件），因此「响应里的 startPeriod 是哪一家」
 * 就等价于「过滤器把上下文设成了哪一家」—— 无需为测试新增任何探针端点。
 *
 * <p><b>断言策略</b>：每个正向场景都配一条负向/前提守卫。只验「切换成功返 200」
 * 无法排除「过滤器根本没跑」；只验「拒绝返 403」无法排除「所有请求都返 403」。
 *
 * <p><b>反证矩阵（AGENTS §2.3：跳过 RED 验证 = 违规，故逐条注入缺陷实测转红）</b>
 * <pre>
 * 注入的缺陷                                          转红的用例
 * ────────────────────────────────────────────────────────────────────
 * ① 去掉成员校验（allowed 恒 true）                    AT-106-7 ×2（403 → 200）
 * ② 不改写上下文（enterpriseId = requested 删掉）       AT-106-6 / AT-106-8 /
 *                                                      跨企业写拒绝 / 观测守卫
 * ③ 审计多写一条                                       AT-106-6 的前提守卫
 * ④ 给 UserEntity.enterpriseId 补 @TableField(fill)     夹具前提守卫
 * </pre>
 * 注入 ② 一处缺陷同时打红 4 条 ⇒ 这些用例确实在观察「上下文」，不是恒绿。
 *
 * <p><b>过程中被自己的守卫抓到的两个自身错误</b>（记录在此以免重犯）：
 * ①观测守卫初版用普通用户造数，未授权切换先撞 403，掩盖了要验的东西 ⇒ 改用 SUPER_ADMIN；
 * ②无上下文自检初版忘记基类 {@code @BeforeEach} 会预置上下文企业 1，
 * 而过滤器只 {@code set} 从不清 ⇒ 端点返回 200 + 全 null，<b>看起来通过实则什么都没验</b>。
 * 另：中文 msg 逐字比对会因响应未带 charset 而按 ISO-8859-1 解码，只断言 code。
 *
 * @see docs/specs/P106-multi-book-account-set.md §7.1 AT-106-6/7/8、§8 批次 2
 * @see docs/development/plans/2026-10-05-P106-multi-book-plan.md §3
 */
@AutoConfigureMockMvc(addFilters = true)
@DisplayName("P106 批次2：企业切换鉴权与跨企业隔离")
class TenantSwitchRealDBTest extends AbstractMapperTest {

    /** 企业 A 的建账期��，用于从 current-period 响应反推上下文企业 */
    private static final String START_PERIOD_A = "209801";
    private static final String START_PERIOD_B = "209802";

    private static final String PREPAY_SUMMARY_PREFIX = "9999.P106.B2.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AgencyUserMapper agencyUserMapper;

    private Long enterpriseA;
    private Long enterpriseB;

    @BeforeEach
    void createTwoEnterprises() {
        enterpriseA = insertEnterprise("P106-B2-A", START_PERIOD_A);
        enterpriseB = insertEnterprise("P106-B2-B", START_PERIOD_B);
    }

    private Long insertEnterprise(String code, String startPeriod) {
        jdbcTemplate.update(
                "INSERT INTO t_enterprise (enterprise_code, enterprise_name, mode, status, start_period) "
                        + "VALUES (?, ?, 'SME', 'ACTIVE', ?)",
                code + "-" + System.nanoTime(), "P106批次2企业" + code, startPeriod);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM t_enterprise WHERE enterprise_code LIKE ? ORDER BY id DESC LIMIT 1",
                Long.class, code + "-%");
    }

    // ==================== 夹具 ====================

    /**
     * 建一个「直属企业 = homeEnterpriseId」的用户。
     *
     * <p>⚠️ <b>为何不用基类 {@code createSysUser}</b>：它把 {@code enterprise_id}
     * 硬写成 {@code DEFAULT_ENTERPRISE_ID}(1)，而本类需要用户分别隶属 A / B 两个企业。
     */
    private Long createUserWithHomeEnterprise(String prefix, Long homeEnterpriseId, String userType) {
        UserEntity user = new UserEntity();
        user.setUsername(prefix + "-" + System.nanoTime());
        user.setPassword("encoded");
        user.setRealName("P106批次2用户");
        user.setStatus("ACTIVE");       // chk_user_status
        user.setUserType(userType);     // chk_user_type: SUPER_ADMIN / AGENCY / ENTERPRISE
        user.setEnterpriseId(homeEnterpriseId);
        user.setDeleted(0);
        withoutEnterpriseContext(() -> userMapper.insert(user));
        return user.getId();
    }

    /** 造一个「与任何企业都无关联」的普通用户 */
    private Long createStranger() {
        return createUserWithHomeEnterprise("P106.STRANGER", null, "ENTERPRISE");
    }

    /** 通过代理成员表把 userId 授权到 enterpriseId（源 2：代理授权） */
    private void grantThroughAgency(Long userId, Long enterpriseId) {
        AgencyUserEntity agencyUser = new AgencyUserEntity();
        agencyUser.setAgencyId(1L);
        agencyUser.setUserId(userId);
        agencyUser.setAgencyRole("ACCOUNTANT");
        agencyUser.setStatus("ACTIVE");
        withoutEnterpriseContext(() -> agencyUserMapper.insert(agencyUser));

        // assigned_by 为 NOT NULL（种子行由 admin 分配）
        jdbcTemplate.update(
                "INSERT INTO t_agency_user_enterprise "
                        + "(agency_user_id, enterprise_id, assigned_by, assigned_at, deleted) "
                        + "VALUES (?, ?, 1, NOW(), 0)", agencyUser.getId(), enterpriseId);
    }

    /**
     * 在指定企业的上下文下造一笔预付款（切上下文而非硬塞实体，AGENTS §4.5 第 23 条）。
     *
     * <p>用 Summary 唯一前缀标记，使后续断言能精确捞回本行而不越界到全表
     * （AGENTS §4.4 第 16 条：裸 {@code count(*)} 断言不可靠）。
     */
    @Autowired
    private com.huicai.sme.arap.mapper.PrepaymentMapper prepaymentMapper;

    private Long createPrepayment(Long enterpriseId, String tag) {
        useEnterprise(enterpriseId);
        com.huicai.sme.arap.entity.PrepaymentEntity e =
                new com.huicai.sme.arap.entity.PrepaymentEntity();
        e.setPeriod("209801");
        e.setTxDate(java.time.LocalDate.now());
        e.setAmount(new java.math.BigDecimal("100.00"));
        e.setSummary(PREPAY_SUMMARY_PREFIX + tag);
        e.setVendorId(1L);
        prepaymentMapper.insert(e);
        return e.getId();
    }

    private String tokenFor(Long userId, String username, Long jwtEnterpriseId, String userType) {
        return jwtProvider.generateAccessToken(username, userId, List.of(),
                jwtEnterpriseId, null, userType, null);
    }

    private String usernameOf(Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT username FROM t_user WHERE id = ?", String.class, userId);
    }

    /** 当前上下文企业的建账期间 —— 即「过滤器把上下文设成了谁」的观测点 */
    private String observedStartPeriod(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        com.fasterxml.jackson.databind.JsonNode data =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(body).path("data");
        return data.path("startPeriod").asText(null);
    }

    private int auditTrailCount(Long userId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_audit_log WHERE module = 'ENTERPRISE' "
                        + "AND operation = 'SWITCH' AND operator_id = ?", Integer.class, userId);
        return n == null ? 0 : n;
    }

    // ==================== AT-106-6：成员切换成功 ====================

    @Test
    @DisplayName("AT-106-6 成员切换到已授权企业 ⇒ 上下文被改写为该企业，且只返回该企业的数据")
    void memberSwitchRewritesContextAndIsolatesData() throws Exception {
        Long uid = createUserWithHomeEnterprise("P106.MEMBER", enterpriseA, "ENTERPRISE");
        grantThroughAgency(uid, enterpriseB);
        String token = tokenFor(uid, usernameOf(uid), enterpriseA, "ENTERPRISE");

        // 先在 A / B 各造一行预付款，用于验证「切到 B 后看不到 A 的行」
        Long prepayA = createPrepayment(enterpriseA, "ISO-A");
        Long prepayB = createPrepayment(enterpriseB, "ISO-B");
        assertNotNull(prepayA);
        assertNotNull(prepayB);

        // 带 X-Enterprise-Id: B 发起请求 ⇒ 上下文应被改写成 B
        MvcResult result = mockMvc.perform(get("/api/v1/enterprise/current-period")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Enterprise-Id", String.valueOf(enterpriseB)))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(START_PERIOD_B, observedStartPeriod(result),
                "带 X-Enterprise-Id=" + enterpriseB + " 时上下文应被改写为该企业，"
                        + "响应的 startPeriod 即上下文企业的建账期间");

        // 负向：切换到 B 后，A 的预付款不得出现在查询结果里
        String body = mockMvc.perform(get("/api/sme/arap/v1/prepayment/page")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Enterprise-Id", String.valueOf(enterpriseB)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(body.contains(String.valueOf(prepayB)),
                "切换到企业 B 后应能查到 B 自己的预付款 " + prepayB + "；实际响应=" + body);
        assertTrue(!body.contains("\"id\":" + prepayA),
                "切换到企业 B 后仍查到企业 A 的预付款 " + prepayA + " ⇒ 隔离失效；实际响应=" + body);
    }

    @Test
    @DisplayName("AT-106-6 前提守卫：切换成功后必须留下审计记录（否则「拒绝时无记录」恒真）")
    void allowedSwitchIsAudited() throws Exception {
        Long uid = createUserWithHomeEnterprise("P106.AUDIT", enterpriseA, "ENTERPRISE");
        grantThroughAgency(uid, enterpriseB);
        String token = tokenFor(uid, usernameOf(uid), enterpriseA, "ENTERPRISE");

        int before = auditTrailCount(uid);

        mockMvc.perform(get("/api/v1/enterprise/current-period")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Enterprise-Id", String.valueOf(enterpriseB)))
                .andExpect(status().isOk());

        assertEquals(before + 1, auditTrailCount(uid),
                "放行的切换未留痕 —— 则 AT-106-7 的「拒绝时无成功记录」断言恒真，失去意义。"
                        + "（EnterpriseSwitchAuditService 用 REQUIRES_NEW 独立事务写入）");
    }

    // ==================== AT-106-7：非成员切换被拒 ====================

    @Test
    @DisplayName("AT-106-7 非成员切换到未授权企业 ⇒ 403 且审计无成功记录")
    void nonMemberSwitchDeniedWithNoAuditTrail() throws Exception {
        Long uid = createStranger();
        String token = tokenFor(uid, usernameOf(uid), null, "ENTERPRISE");

        int before = auditTrailCount(uid);

        mockMvc.perform(get("/api/v1/enterprise/current-period")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Enterprise-Id", String.valueOf(enterpriseB)))
                .andExpect(status().isForbidden());

        assertEquals(before, auditTrailCount(uid),
                "越权切换被拒却留下了成功留痕 ⇒ 审计记录的是「尝试过」而非「成功」，"
                        + "下游据此判断切换历史会得到错误结论");
    }

    @Test
    @DisplayName("AT-106-7 前提守卫：被拒请求不得改写上下文（否则「拒绝」只是装饰）")
    void deniedSwitchDoesNotRewriteContext() throws Exception {
        Long uid = createUserWithHomeEnterprise("P106.DENIED-CTX", enterpriseA, "ENTERPRISE");
        String token = tokenFor(uid, usernameOf(uid), enterpriseA, "ENTERPRISE");

        // 该用户只隶属 A，未获授权 B ⇒ 切 B 必须被拒，且上下文仍应是 JWT 里的 A
        mockMvc.perform(get("/api/v1/enterprise/current-period")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Enterprise-Id", String.valueOf(enterpriseB)))
                .andExpect(status().isForbidden());

        MvcResult after = mockMvc.perform(get("/api/v1/enterprise/current-period")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(START_PERIOD_A, observedStartPeriod(after),
                "被拒的切换请求之后，上下文应仍为 JWT 内的企业 " + enterpriseA);
    }

    // ==================== AT-106-8：SUPER_ADMIN 切换 ====================

    @Test
    @DisplayName("AT-106-8 SUPER_ADMIN 带 X-Enterprise-Id ⇒ 会设置上下文为该企业")
    void superAdminSwitchSetsContext() throws Exception {
        Long uid = createUserWithHomeEnterprise("P106.SUPER", enterpriseA, "SUPER_ADMIN");
        String token = tokenFor(uid, usernameOf(uid), enterpriseA, "SUPER_ADMIN");

        MvcResult result = mockMvc.perform(get("/api/v1/enterprise/current-period")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Enterprise-Id", String.valueOf(enterpriseB)))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(START_PERIOD_B, observedStartPeriod(result),
                "SUPER_ADMIN 应可切换到任意企业，上下文须被改写为 " + enterpriseB
                        + "（V1.1 曾误写「不设置上下文」，已按 JwtAuthenticationFilter:120-122 修正）");
    }

    @Test
    @DisplayName("AT-106-8 负向：不带 X-Enterprise-Id 时，上下文按 JWT 内的企业")
    void superAdminWithoutHeaderUsesJwtEnterprise() throws Exception {
        Long uid = createUserWithHomeEnterprise("P106.SUPER-NOHD", enterpriseA, "SUPER_ADMIN");
        String token = tokenFor(uid, usernameOf(uid), enterpriseA, "SUPER_ADMIN");

        MvcResult result = mockMvc.perform(get("/api/v1/enterprise/current-period")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(START_PERIOD_A, observedStartPeriod(result),
                "不带该头时上下文应等于 JWT 内的企业 " + enterpriseA
                        + "；若等于 " + enterpriseB + " 说明头部逻辑串味");
    }

    // ==================== 跨企业写拒绝 ====================

    @Test
    @DisplayName("跨企业写拒绝：A 企业的记录在 B 上下文下不可确认（写入隔离的最后一环）")
    void crossEnterpriseWriteIsRejected() throws Exception {
        Long uid = createUserWithHomeEnterprise("P106.XWRITE", enterpriseA, "ENTERPRISE");
        grantThroughAgency(uid, enterpriseB);
        String token = tokenFor(uid, usernameOf(uid), enterpriseA, "ENTERPRISE");

        // 在 A 上下文下建一笔预付款
        MvcResult created = mockMvc.perform(post("/api/sme/arap/v1/prepayment")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Enterprise-Id", String.valueOf(enterpriseA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"209801\",\"amount\":100.00,"
                                + "\"summary\":\"" + PREPAY_SUMMARY_PREFIX + "XWRITE\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String createdBody = created.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(createdBody.contains("\"code\":200"), "创建预付款失败：" + createdBody);
        Long prepayId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(createdBody).path("data").path("id").asLong();
        assertTrue(prepayId > 0, "未取到预付款 id：" + createdBody);

        // 切到 B 上下文后，该记录必须不可见 ⇒ 确认动作应失败
        MvcResult confirm = mockMvc.perform(
                        post("/api/sme/arap/v1/prepayment/" + prepayId + "/confirm")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Enterprise-Id", String.valueOf(enterpriseB)))
                .andReturn();
        String confirmBody = confirm.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(!confirmBody.contains("\"code\":200"),
                "在企业 B 上下文下竟成功确认了企业 A 的预付款 " + prepayId
                        + " ⇒ 跨企业写未被拒绝；实际响应=" + confirmBody);

        // 负向：A 的记录状态不得被这次越权确认改动
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM t_prepayment WHERE id = ?", String.class, prepayId);
        assertEquals("DRAFT", status,
                "越权确认虽报错，但记录状态已被改动 ⇒ 「拒绝」只是响应层面的装饰");
    }

    // ==================== 前提守卫 ====================

    /**
     * 前提守卫：{@code t_user.enterprise_id} 是「归属企业」，不得被上下文改写。
     *
     * <p><b>反证记录（必读，勿照抄结论）</b>：本守卫最初写成「{@code insertFill} 会把
     * {@code enterpriseId} 无条件覆盖为上下文」，据此认为
     * {@code withoutEnterpriseContext(...)} 包裹是必需的。<b>实测该前提为假</b> ——
     * 去掉包裹后本守卫<b>依然绿</b>。追到字段映射层才看清真实机制：
     * {@code UserEntity} <b>不继承 {@code BaseEntity}</b>（自带 id/createdAt/deleted）
     * 且全类<b>无任何 {@code @TableField}</b> ⇒ MyBatis-Plus 的
     * {@code TableInfo.withInsertFill = false} ⇒ {@code MyMetaObjectHandler.insertFill}
     * <b>根本不被调用</b>（不是「调用了但没填」，是「压根没进」，AGENTS §4.5 第 34 条）。
     *
     * <p><b>决定性反证</b>：给 {@code UserEntity.enterpriseId} 临时补上
     * {@code @TableField(fill = FieldFill.INSERT)} 后，本守卫立即转红
     * （{@code expected: <18> but was: <1>}）⇒ 证明「一旦该实体启用 fill 机制，
     * 归属企业立刻被上下文吞掉」。
     *
     * <p><b>两条结论</b>：①包裹<b>不是</b>必需的，但保留 —— 它表达的是「本夹具刻意
     * 跨租户造数」的意图，且不依赖上面那个脆弱的机制假设；②真正的风险不在测试侧，而在
     * <b>生产侧</b>：若日后有人「顺手」给该字段补 {@code fill} 注解以统一风格，
     * 用户归属企业会被静默改写成操作人当前企业 ⇒ <b>建账号时指定企业这一功能失效</b>。
     * 本守卫就是拦住该回退的闸门。
     */
    @Test
    @DisplayName("前提守卫：t_user.enterprise_id 是「归属企业」，不得被 fill 机制改写为上下文")
    void userHomeEnterpriseIsNotOverwrittenByContext() {
        Long uid = createUserWithHomeEnterprise("P106.HOME", enterpriseA, "ENTERPRISE");

        Long persisted = jdbcTemplate.queryForObject(
                "SELECT enterprise_id FROM t_user WHERE id = ?", Long.class, uid);

        assertEquals(enterpriseA, persisted,
                "用户归属企业被改写为 " + persisted + "，夹具前提不成立，"
                        + "本类其余用例均退化为假绿");
    }

    @Test
    @DisplayName("前提守卫：两个测试企业真实存在且建账期间不同（否则「上下文被改写」无从观测）")
    void enterprisesAreDistinctWithDistinctStartPeriods() {
        assertNotNull(enterpriseA);
        assertNotNull(enterpriseB);
        assertTrue(!enterpriseA.equals(enterpriseB), "两个企业 id 相同");
        assertEquals(START_PERIOD_A, jdbcTemplate.queryForObject(
                "SELECT start_period FROM t_enterprise WHERE id = ?", String.class, enterpriseA));
        assertEquals(START_PERIOD_B, jdbcTemplate.queryForObject(
                "SELECT start_period FROM t_enterprise WHERE id = ?", String.class, enterpriseB));
    }

    /**
     * 观测手段自身的守卫：{@code current-period} 必须<b>随上下文变化</b>。
     *
     * <p>本类所有「上下文被改写 / 未被改写」的断言都建立在
     * 「响应里的 startPeriod == 上下文企业的建账期间」这一映射上。若该端点某天改为
     * 读 JWT、读默认值、或对所有企业返回同一个常量，则上面那些断言会<b>集体恒绿</b>
     * —— 且看不出任何异常。故必须单独证明这条映射此刻成立。
     */
    @Test
    @DisplayName("观测手段守卫：current-period 的 startPeriod 确实随企业上下文而变")
    void currentPeriodEndpointActuallyReflectsContext() throws Exception {
        // 用 SUPER_ADMIN：本守卫要验的是「观测手段能否区分上下文」，
        // 不应掺入成员授权（否则未授权切换会先撞 403，掩盖真正要验的东西）
        Long uid = createUserWithHomeEnterprise("P106.PROBE", enterpriseA, "SUPER_ADMIN");
        String token = tokenFor(uid, usernameOf(uid), enterpriseA, "SUPER_ADMIN");

        String withoutHeader = observedStartPeriod(mockMvc.perform(
                        get("/api/v1/enterprise/current-period")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
        String withHeaderB = observedStartPeriod(mockMvc.perform(
                        get("/api/v1/enterprise/current-period")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Enterprise-Id", String.valueOf(enterpriseB)))
                .andExpect(status().isOk())
                .andReturn());

        assertEquals(START_PERIOD_A, withoutHeader, "JWT 上下文为企业 A");
        assertEquals(START_PERIOD_B, withHeaderB, "带头切到企业 B");
        assertTrue(!withoutHeader.equals(withHeaderB),
                "两次请求返回了同一个 startPeriod=" + withoutHeader
                        + " ⇒ 该端点无法区分上下文，本类 AT-106-6/8 的断言全部恒真");
    }

    /**
     * 反向自检：证明 {@code current-period} 在<b>无上下文</b>时确实失败，
     * 从而排除「该端点根本不读上下文、只是碰巧返回了某个企业的数据」。
     *
     * <p>⚠️ <b>必须先显式清上下文，否则本用例恒绿</b>：
     * {@code AbstractMapperTest} 的 {@code @BeforeEach} 会把上下文预置为
     * {@code DEFAULT_ENTERPRISE_ID}(1)，而 {@code JwtAuthenticationFilter} 只在
     * {@code enterpriseId != null} 时 {@code set}、<b>从不清除已存在的值</b>
     * （它只在 {@code finally} 里清自己那一份）⇒ JWT 无企业时，请求仍会带着基类
     * 预置的企业 1 进去，端点返回 200 + 一堆 null，看起来「通过」实则完全没验到东西。
     * 这是<b>测试夹具产物而非生产缺陷</b>：生产里该 {@code finally} 保证每个请求
     * 起始上下文为 null（Tomcat 线程复用场景亦然）。
     */
    @Test
    @DisplayName("反向自检：无上下文时 current-period 返 400（证明它确实读上下文）")
    void currentPeriodFailsWithoutContext() throws Exception {
        Long uid = createUserWithHomeEnterprise("P106.NOCTX", enterpriseA, "ENTERPRISE");
        // JWT 内 enterpriseId 置 null —— 模拟「超管未选企业」的真实首登场景
        String token = tokenFor(uid, usernameOf(uid), null, "ENTERPRISE");

        EnterpriseContextHolder.clear();
        MvcResult result = mockMvc.perform(get("/api/v1/enterprise/current-period")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        // 只断言 code，不逐字比对 msg：msg 是中文，而 GlobalExceptionHandler 走 Jackson
        // 输出时响应未显式带 charset，getContentAsString() 会按 ISO-8859-1 解码
        // ⇒ 比对中文等于在断言一个解码假象（实测踩过：期望串与实际串只差编码）。
        assertEquals(400, jsonOf(result).path("code").asInt(),
                "无上下文时该端点未按预期拒绝 —— 说明它并不以 EnterpriseContextHolder 为入参，"
                        + "本类的上下文观测手段失效");
    }

    private com.fasterxml.jackson.databind.JsonNode jsonOf(MvcResult result) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
}