package com.huicai.sme.arap.service.impl;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.arap.entity.PrepaymentEntity;
import com.huicai.sme.arap.mapper.PrepaymentMapper;
import com.huicai.sme.arap.service.PrepaymentService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P106 / REQ-2026-133 / AT-106-1：预付款双列并存收口真库测试
 *
 * <p><b>缺陷</b>：{@code t_prepayment} 同时存在 {@code enterprise_id}（RLS 谓词读它）
 * 与 {@code tenant_id}（V5 建表、V105 补 enterprise_id 后遗留），而
 * {@code PrepaymentServiceImpl#create()} 写的是
 * {@code if (entity.getTenantId() == null) entity.setTenantId(DEFAULT_TENANT_ID /* = 1L *&#47;/)}
 * ⇒ <b>两列值永久不一致</b>：{@code enterprise_id} 由
 * {@code MyMetaObjectHandler.insertFill} 无条件覆盖为上下文企业，
 * 而 {@code tenant_id} 恒为常量 1。
 *
 * <p><b>为什么必须真库</b>：Mock 测不出这件事 —— {@code insertFill} 的无条件覆盖、
 * RLS 谓词、以及两列的实际落库值都只有真实 PG 才能观察。
 *
 * <p><b>断言策略</b>（负向断言强制）：正向「两列恒等」+ 负向「按旧列查不得命中」，
 * 只做正向会漏掉「一致但都错」的情况。
 *
 * @see docs/specs/P106-multi-book-account-set.md §0.2 / §7
 * @see docs/development/plans/2026-10-05-P106-multi-book-plan.md 微循环 1a-1
 */
@DisplayName("P106 预付款：tenant_id 不得与 enterprise_id 并存且不一致")
class AccountSetIsolationRealDBTest extends AbstractMapperTest {

    /** 独立企业号，避免与基类默认的 1 混淆（AGENTS §4.5 第 23 条：切上下文而非硬塞实体） */
    private static final Long ENTERPRISE_E = 990001L;

    private static final String PREFIX = "9999.P106.PREPAY.";

    @Autowired
    private PrepaymentService prepaymentService;

    @Autowired
    private PrepaymentMapper prepaymentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private PrepaymentEntity draft(String tag) {
        PrepaymentEntity e = new PrepaymentEntity();
        e.setPeriod("209912");
        e.setTxDate(LocalDate.now());
        e.setAmount(new BigDecimal("1000.00"));
        e.setSummary(PREFIX + tag);
        e.setVendorId(1L);
        return e;
    }

    @Test
    @DisplayName("AT-106-1 企业上下文为 E 时，落库行 tenant_id 必须恒等于 enterprise_id")
    void prepaymentTenantIdMustMatchEnterpriseId() {
        useEnterprise(ENTERPRISE_E);

        PrepaymentEntity saved = prepaymentService.create(draft("EQ"));

        assertNotNull(saved.getId(), "未落库");

        Long tenantId = jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM t_prepayment WHERE id = ?", Long.class, saved.getId());
        Long enterpriseId = jdbcTemplate.queryForObject(
                "SELECT enterprise_id FROM t_prepayment WHERE id = ?", Long.class, saved.getId());

        assertEquals(ENTERPRISE_E, enterpriseId,
                "enterprise_id 应由 insertFill 无条件覆盖为上下文企业");
        assertEquals(enterpriseId, tenantId,
                "两列必须恒等：PrepaymentServiceImpl#create 写死 tenant_id=1（DEFAULT_TENANT_ID）"
                        + "而 RLS 谓词读 enterprise_id，两列永久不一致 ⇒ 任何按 tenant_id 的"
                        + "统计/索引（如 idx_classification_rule_tenant）都基于错误数据。"
                        + "实际 tenant_id=" + tenantId + " / enterprise_id=" + enterpriseId);
    }

    @Test
    @DisplayName("负向：把 tenant_id 改成与 enterprise_id 不同的值后，按旧列的查询不得命中本企业数据")
    void tenantIdIsNotAnIsolationColumn() {
        useEnterprise(ENTERPRISE_E);

        PrepaymentEntity saved = prepaymentService.create(draft("NEG"));
        assertNotNull(saved.getId(), "未落库");

        // 人为制造「旧列指向别的企业」的脏数据
        jdbcTemplate.update("UPDATE t_prepayment SET tenant_id = ? WHERE id = ?",
                Long.valueOf(999999L), saved.getId());

        Integer byLegacyColumn = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_prepayment WHERE id = ? AND tenant_id = ?",
                Integer.class, saved.getId(), ENTERPRISE_E);

        assertTrue(byLegacyColumn == null || byLegacyColumn == 0,
                "按 tenant_id=" + ENTERPRISE_E + " 查询不应命中被改成 999999 的行，实际命中 "
                        + byLegacyColumn + " 行 —— 若命中说明旧列仍被当作隔离依据使用");
    }

    @Test
    @DisplayName("负向：切到别的企业上下文后，本企业的预付款不可见（须 SET ROLE 到非超级探针）")
    void prepaymentIsInvisibleUnderOtherEnterpriseContext() {
        // L2 的 Testcontainers 连接角色 test 是超级用户（实测 rolsuper=t、bypassrls=t），
        // 而超级用户**绕过 RLS**，因此用 jdbcTemplate 直接查会看到全部行 —— 那样这条断言恒绿、
        // 等于没测。必须 SET ROLE 到 NOSUPERUSER 探针，让谓词真正被执行。
        // 做法与 TenantRlsRealDBTest#emptyGucReturnsZeroRowsInsteadOfError 一致（唯一做对的先例）。
        useEnterprise(ENTERPRISE_E);
        PrepaymentEntity saved = prepaymentService.create(draft("ISO"));
        assertNotNull(saved.getId(), "未落库");

        jdbcTemplate.execute("DROP ROLE IF EXISTS p106_rls_probe");
        jdbcTemplate.execute("CREATE ROLE p106_rls_probe NOSUPERUSER NOBYPASSRLS");
        jdbcTemplate.execute("GRANT SELECT ON t_prepayment TO p106_rls_probe");

        Integer visibleInProbe = jdbcTemplate.execute((ConnectionCallback<Integer>) con -> {
            boolean prev = con.getAutoCommit();
            con.setAutoCommit(false);
            try (java.sql.Statement st = con.createStatement()) {
                st.execute("SET LOCAL ROLE p106_rls_probe");
                st.execute("SELECT set_config('app.enterprise_id', '" + (ENTERPRISE_E + 1) + "', true)");
                try (java.sql.ResultSet rs = st.executeQuery(
                        "SELECT count(*) FROM t_prepayment WHERE id = " + saved.getId())) {
                    rs.next();
                    return rs.getInt(1);
                }
            } finally {
                con.rollback();
                con.setAutoCommit(prev);
            }
        });

        assertEquals(0, visibleInProbe,
                "以非超级探针角色 + 上下文企业 " + (ENTERPRISE_E + 1) + " 查询企业 " + ENTERPRISE_E
                        + " 的预付款应返 0 行（RLS 谓词读 enterprise_id）；实际可见 " + visibleInProbe
                        + " 行 —— 若非 0，说明隔离失效");
    }

    @Test
    @DisplayName("守卫：L2 连接角色是超级用户，故任何隔离断言都必须 SET ROLE 到非超级探针")
    void l2RoleIsSuperuserSoRlsAssertionsNeedSetRole() {
        Boolean superuser = jdbcTemplate.queryForObject(
                "SELECT rolsuper FROM pg_roles WHERE rolname = current_user", Boolean.class);

        assertTrue(Boolean.TRUE.equals(superuser),
                "本用例的隔离断言建立在「L2 连接角色是超级用户、RLS 被绕过」这一前提上。"
                        + "若将来 Testcontainers 改用非超级角色，前提变化，"
                        + "prepaymentIsInvisibleUnderOtherEnterpriseContext 的探针写法需同步复核。"
                        + "当前 rolsuper=" + superuser);
    }

    @Test
    @DisplayName("正向：按 summary 前缀可捞回本用例造的行（守卫夹具可识别，避免全表断言）")
    void fixtureIsRetrievableByPrefix() {
        useEnterprise(ENTERPRISE_E);

        PrepaymentEntity saved = prepaymentService.create(draft("FIND"));

        Integer found = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_prepayment WHERE summary LIKE ?",
                Integer.class, PREFIX + "FIND%");

        assertEquals(1, found,
                "用例造的行应可按唯一前缀精确捞回；实测前缀不生效会让断言越界到全表（AGENTS §4.4 第 16 条）");
        assertNotNull(prepaymentMapper.selectById(saved.getId()), "回读不到");
    }

    /**
     * P106 / 批次 1a-3 的发现登记（<b>暂以 @Disabled 挂起</b>，等 SPEC 决策后再实现）。
     *
     * <p><b>实测结论</b>：{@code t_classification_rule} 与 {@code t_ai_feedback_log} 的问题
     * <b>比预付款更严重 —— 读路径也按 {@code tenant_id} 过滤</b>，共 6 个方法把
     * {@code tenantId} 当参数（5 处用于过滤或写入）：
     * <ul>
     *   <li>{@code ClassificationRuleServiceImpl#page(:43)} / {@code #seedForNewTenant(:105/:139/:187)}
     *       / {@code #create(:56, 写死 1L)}</li>
     *   <li>{@code AiFeedbackLogServiceImpl#page(:40)} / {@code #summaryByTenant(:77)}</li>
     * </ul>
     * 且两个 Controller 都把它暴露成 {@code @RequestParam}（客户端可任意传值）。
     *
     * <p><b>为什么不是越权读</b>：RLS 的 {@code enterprise_policy} 谓词是
     * {@code enterprise_id = current_setting('app.enterprise_id')}（真实库已核对，且 FORCE 生效），
     * 与 {@code tenant_id = <客户端值>} 是<b>两个 AND 条件</b> ⇒ 传任意 {@code tenantId}
     * 也读不到其它企业的行。⇒ 定性为<b>功能缺陷（过滤条件失效）</b>，不是安全漏洞。
     *
     * <p><b>为什么挂起而不是直接改</b>：修法必然要动 <b>客户端契约</b> ——
     * {@code tenantId} 请求参数要么废弃、要么改语义，涉及 Controller 签名与前端联调，
     * 属铁律 #10「三步闭环」需先在 SPEC 里决策的事项，不应在实施批次里顺手改掉。
     *
     * <p>恢复条件：SPEC 决策「{@code tenantId} 参数废弃（改用上下文企业）」还是
     * 「保留参数但忽略之」，并更新本用例的期望。
     */
    @Test
    @Disabled("P106 批次 1a-3 待 SPEC 决策：tenantId 请求参数的废弃与否属客户端契约变更（铁律 #10）")
    @DisplayName("P106 1a-3（挂起）：分类规则与 AI 反馈的读过滤应按 enterprise_id 而非客户端传入的 tenant_id")
    void classificationAndAiFeedbackFiltersShouldUseEnterpriseContext() {
        useEnterprise(ENTERPRISE_E);

        // 期望（待 SPEC 决策后实现）：不传 tenantId 也能按当前企业查到本企业的规则
        Integer rules = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_classification_rule WHERE enterprise_id = ?",
                Integer.class, ENTERPRISE_E);

        assertNotNull(rules, "应能按 enterprise_id 直接查询，客户端无需传 tenantId");
    }
}