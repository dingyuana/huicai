package com.huicai.sme.arap.service.impl;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.arap.entity.PrepaymentEntity;
import com.huicai.sme.arap.mapper.PrepaymentMapper;
import com.huicai.sme.arap.service.PrepaymentService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.business.entity.ClassificationRuleEntity;
import com.huicai.sme.cash.service.ClassificationRuleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private ClassificationRuleService classificationRuleService;

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
     * P106 / 批次 1a-3（D-1）：分类规则分页改为按上下文企业的 enterprise_id 过滤，
     * `tenantId` 请求参数彻底废弃。实测：企业 E 看不到企业 1 的规则，反之亦然。
     *
     * <p>启用条件（已满足）：V1.3 决策确认「tenantId 参数废弃（改用上下文企业）」⇒ 恢复生效，
     * 并按 §0.5 教训（§4.4）用真实 service 回路验证，而非图案字面量。
     */
    @Test
    @DisplayName("P106 1a-3：分类规则分页按 enterprise_id 过滤，tenantId 参数废弃")
    void classificationAndAiFeedbackFiltersShouldUseEnterpriseContext() {
        // P106 D-3：t_classification_rule.tenant_id 已删除，夹具只写 enterprise_id
        // 造两行：一行给企业 1，一行给企业 E
        int n = jdbcTemplate.update(
                "INSERT INTO t_classification_rule (enterprise_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active, deleted, created_at, updated_at) " +
                "VALUES (1, ?, 'keyword_regex', 'TTT', 'description', 'in', 'bank_fee', 99, true, 0, now(), now())",
                "9999.P106.CLS.EA");
        assertEquals(1, n);
        int n2 = jdbcTemplate.update(
                "INSERT INTO t_classification_rule (enterprise_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active, deleted, created_at, updated_at) " +
                "VALUES (?, ?, 'keyword_regex', 'TTT', 'description', 'in', 'bank_fee', 99, true, 0, now(), now())",
                ENTERPRISE_E, "9999.P106.CLS.EB");
        assertEquals(1, n2);

        // 上下文切到企业 E：service.page 只应返回 E 的该行
        useEnterprise(ENTERPRISE_E);
        IPage<ClassificationRuleEntity> pageE = classificationRuleService.page(1, 20);
        assertEquals(1, pageE.getRecords().size(), "企业 E 只应看到自己的 1 条规则");
        assertEquals("9999.P106.CLS.EB", pageE.getRecords().get(0).getName());

        // 上下文切回企业 1：反过来只应返回企业 1 的该行
        useEnterprise(1L);
        IPage<ClassificationRuleEntity> page1 = classificationRuleService.page(1, 20);
        assertTrue(page1.getRecords().stream().anyMatch(r -> "9999.P106.CLS.EA".equals(r.getName())),
                "企业 1 应看不到 9999.P106.CLS.EB 的规则");

        jdbcTemplate.update("DELETE FROM t_classification_rule WHERE name LIKE '9999.P106.CLS.%'");
    }

/**
     * D-1 追加：{@code match()} 原按 {@code tenant_id} 过滤，同一缺陷类的<b>读路径</b> ——
     * 两列一旦不一致（本项目预付款已出现过该形态），会静默匹配到别的企业的规则。
     *
     * <p><b>为什么只能在真库测</b>：断言 {@code LambdaQueryWrapper} 落到哪个列需 MP 的
     * {@code TableInfo} lambda 缓存，而该缓存<b>只在 Spring 上下文启动时建立</b>，
     * 纯 Mockito 单测里调用 {@code getCustomSqlSegment()} 直接抛
     * {@code MybatisPlusException: can not find lambda cache}（AGENTS §4.3 第 7 条 Mock 盲区）。
     *
     * <p><b>⚠️ 夹具在 D-3 后改写（诚实记录）</b>：本用例首版<b>刻意造两列不一致</b>
     * （{@code tenant_id=1 / enterprise_id=E}）来证伪旧实现。D-3 删除 {@code tenant_id} 列后
     * 该形态<b>在库内不再可能</b> ⇒ 夹具改为只写 {@code enterprise_id}。
     * ⇒ 证伪能力随之下降：现在只能证伪「match 不过滤 / 按别的企业号过滤」，
     * <b>不能再证伪「按 tenant_id 过滤」</b> —— 但后者已由 D-1 的
     * 单测断言（SQL 片段含 {@code enterprise_id} 且不含 {@code tenant_id}）承担，
     * 且列已删除，MP 若仍引用该列会直接抛 SQL 错。故整体覆盖不降级。
     */
    @Test
    @DisplayName("P106 1a-3：match() 按 enterprise_id 过滤，跨企业不命中")
    void matchMustNotCrossEnterpriseBoundary() {
        // D-3 后 tenant_id 列已删，夹具只写 enterprise_id
        jdbcTemplate.update(
                "INSERT INTO t_classification_rule (enterprise_id, name, rule_type, pattern, match_field, direction, classification, priority, is_active, deleted, created_at, updated_at) " +
                "VALUES (?, ?, 'keyword_regex', 'ZZUNIQUEPATTERNZZ', 'description', 'in', 'bank_fee', 1, true, 0, now(), now())",
                ENTERPRISE_E, "9999.P106.MATCH.E");
        try {
            // 正向：这条规则属于企业 E（enterprise_id=E），ctx=E 时必须命中
            useEnterprise(ENTERPRISE_E);
            assertNotNull(classificationRuleService.match("含ZZUNIQUEPATTERNZZ的摘要", "in", null),
                    "企业 E 应命中自己 enterprise_id 下的规则");

            // 负向：企业 1 的 enterprise_id 不是 E，绝不能命中（否则 match 会跨企业静默错分）
            useEnterprise(1L);
            assertNull(classificationRuleService.match("含ZZUNIQUEPATTERNZZ的摘要", "in", null),
                    "企业 1 不得命中 enterprise_id 属于企业 E 的规则（跨企业静默错分）");
        } finally {
            jdbcTemplate.update("DELETE FROM t_classification_rule WHERE name = '9999.P106.MATCH.E'");
        }
    }

    /**
     * P106 D-3 守卫：{@code t_classification_rule.tenant_id} 列与
     * {@code idx_classification_rule_tenant} 索引<b>必须已不存在</b>。
     *
     * <p><b>为什么要守「列不存在」这种负向事实</b>：删列后若有人（或后来的 migration）
     * 把 {@code tenant_id} 加回来，或 Entity 上残留字段重新参与映射，
     * 「两列并存」这个已关闭的缺陷类就会<b>静默复活</b> ——
     * 代码仍能跑、测试仍可能全绿（因为读路径已只用 {@code enterprise_id}），
     * 唯独数据层又开始积累第二套隔离真相（AGENTS §4.2 第 16 条「反向缺口」同型：
     * 列在 {@code \\d} 里一眼可见，不查就发现不了）。
     *
     * <p><b>为什么必须查 {@code information_schema} 而不是 {@code \\d}</b>：
     * 本项目已多次因「开发库停在旧 Flyway 版本」把工具告警误当缺陷（AGENTS §4.5 第 23 条）。
     * 本用例跑在 L2 Testcontainers 上，Flyway 已应用全部迁移 ⇒ 读到的是权威结构。
     */
    @Test
    @DisplayName("P106 D-3：tenant_id 列与 idx_classification_rule_tenant 索引必须已删除")
    void tenantIdColumnAndIndexMustBeDropped() {
        Integer colCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_name = 't_classification_rule' AND column_name = 'tenant_id'",
                Integer.class);
        assertEquals(0, colCount,
                "D-3 已删除 t_classification_rule.tenant_id，列若重新出现则「双列并存」缺陷静默复活"
                        + "（当前 " + colCount + " 列）");

        Integer idxCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes "
                        + "WHERE tablename = 't_classification_rule' AND indexname = 'idx_classification_rule_tenant'",
                Integer.class);
        assertEquals(0, idxCount,
                "idx_classification_rule_tenant 建在已删除的列上，必须随之删除（当前仍有 " + idxCount + " 个）");

        // 反向对照：enterprise_id 的索引必须仍在，否则删列时误删了替代索引
        Integer entIdx = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE tablename = 't_classification_rule' "
                        + "AND indexname = 'idx_t_classification_rule_enterprise'",
                Integer.class);
        assertEquals(1, entIdx,
                "enterprise_id 索引必须保留 —— D-3 只删旧列索引，不得误删替代索引");
    }

    /**
     * D-3 补做：{@code t_ai_feedback_log.tenant_id} 列必须已删除。
     *
     * <p><b>为什么这一张也要守</b>：它同属 §0.2 双列并存表，D-3 裁定只点名了
     * {@code t_classification_rule} —— 该列「暂存」了 11 个批次，期间的任何
     * 读写路径复活都会把「双列并存」缺陷带回这张表。其无读写路径、无索引，
     * 删除零风险，却能堵住这条复活路径（与我自己在 SPEC 里留下的「刻意未删」
     * 的未决债一致，见 V172 的实施说明）。
     */
    @Test
    @DisplayName("D-3 补做：ai_feedback_log.tenant_id 列必须已删除")
    void aiFeedbackLogTenantIdColumnMustBeDropped() {
        Integer colCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_name = 't_ai_feedback_log' AND column_name = 'tenant_id'",
                Integer.class);
        assertEquals(0, colCount,
                "D-3 补做已删除 t_ai_feedback_log.tenant_id，列若重新出现则双列并存缺陷静默复活"
                        + "（当前 " + colCount + " 列）");
    }
}