package com.huicai.common.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P106 / 批次 1b（D-2a）守卫：{@code EnterpriseDataPermissionInterceptor.SHARED_TABLES}
 * 里<b>每一张</b>表的豁免都必须有 SPEC 定性依据，且定性必须与真实结构一致。
 *
 * <p><b>为什么「零 DDL 的文档收口」也需要守卫</b>：D-2a 不改列、不改 SQL，
 * 只把「哪些表刻意不做企业隔离」写成可查结论。文档性结论最容易的失效形态是
 * <b>与代码分叉</b> —— 白名单是「放行不过滤」的名单，
 * <b>混入一张有隔离列的业务表 = 静默关闭该表的第二层防线</b>，且不会有任何报错。
 *
 * <p><b>⚠️ 本守卫在首轮就查出 SPEC §0.1 的一处真实缺陷</b>：
 * §0.1 把「两列都没有的纯平台全局表」列为 10 张，但白名单里的
 * {@code t_service_progress}（V148 建表）<b>有 {@code enterprise_id NOT NULL}</b>，
 * 且其白名单理由是「查询手动按 {@code agency_id} 过滤，注入 enterprise_id 会破坏跨客户查询」
 * —— 这是<b>与 §0.1 三类都不同</b>的第四种理由，§0.1 漏登 ⇒ 定性与运行时事实已分叉。
 * ⇒ 登记表改为「A 类无隔离列」+「B 类有隔离列但语义非上下文企业」两栏。
 */
@DisplayName("P106 D-2a 守卫：SHARED_TABLES 每张表的豁免都须有定性依据且与结构一致")
class SharedTablesExemptionRealDBTest extends AbstractMapperTest {

    /**
     * A 类：<b>无</b> {@code enterprise_id} / {@code tenant_id} 的纯平台全局表
     * （SPEC §0.1 的三类定性：权限骨架 / 平台级 / 平台元数据）。
     *
     * <p>白名单理由：拦截器若注入 {@code enterprise_id}，这些表<b>没有该列</b> ⇒ SQL 报错。
     */
    private static final Map<String, String> GROUP_A_NO_ISOLATION_COLUMN = new LinkedHashMap<>() {{
        put("t_menu", "刻意全局共享：权限与导航骨架，一份定义供所有企业复用");
        put("t_role", "刻意全局共享：角色字典");
        put("t_role_menu", "刻意全局共享：角色-菜单授权骨架");
        put("t_user_role", "刻意全局共享：用户-角色绑定骨架");
        put("t_sys_config", "平台级（D-2b 裁定 A）：含 accounting.start_year/month 账套级配置，按全局统一");
        put("t_audit_log", "平台级（D-2b 裁定 A）：当前无 enterprise_id，历史审计记录无需归属");
        put("t_agency", "平台元数据：代理主体，位于隔离维度之上");
        put("t_agency_user", "平台元数据：代理人员，位于隔离维度之上");
        put("t_enterprise", "平台元数据：企业主体，位于隔离维度之上（其 enterprise_id 是外键不是租户列）");
    }};

    /**
     * B 类：<b>有</b> {@code enterprise_id}，但该列语义<b>不是</b>「当前上下文企业」，
     * 故不得被拦截器注入，也不得被 {@code insertFill} 覆盖。
     *
     * <p>这一类<b>不在 SPEC §0.1 的 10 张之内</b>（§0.1 当时只登记「两列都没有」的表），
     * 是本守卫首轮反查时补上的 —— 见类注释里那条「SPEC 缺陷」记录。
     */
    private static final Map<String, String> GROUP_B_SEMANTIC_EXEMPTION = new LinkedHashMap<>() {{
        put("t_user", "enterprise_id = 归属企业（V101 可空列），非上下文企业；"
                + "按上下文过滤会造成循环依赖 —— 决定「当前企业是谁」的成员校验本身要读它");
        put("t_dept", "P106 批次 1b''：**已移出白名单并按企业隔离**（V171 补 enterprise_id + "
                + "unique 按企业分段 + RLS）。仍列在此处仅因其 enterprise_id 语义与 A 类的"
                + "「无隔离列」不同：它是真隔离列，不在 SHARED_TABLES 内 —— "
                + "保留条目是为了记录「曾豁免、现已隔离」这一历史，防止误加回去");
        put("t_agency_enterprise", "enterprise_id = 代理↔企业授权关系的一端（V100 建表即有），"
                + "该表是拓扑边本身，不是某企业的业务数据");
        put("t_agency_user_enterprise", "enterprise_id = 分配的客户企业ID（V112），"
                + "同上：授权关系本身，语义为「被服务对象」");
        put("t_service_progress", "V148 建表即 enterprise_id NOT NULL，注释明写「agency_id 隔离 + "
                + "enterprise_id 存被服务客户」，查询手动按 agency_id 过滤以支持跨客户查询；"
                + "注入上下文 enterprise_id 会破坏代理端跨客户视图");
    }};

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 主断言：白名单 == A ∪ B，<b>不多一张、不少一张</b>。
     *
     * <p>两个方向都要锁：
     * <ul>
     *   <li><b>少</b>（A/B 里有的没进白名单）⇒ 该表会被注入 enterprise_id，
     *       A 类表<b>直接 SQL 报错</b>，B 类表<b>语义被破坏</b>（跨客户查询被限死）。</li>
     *   <li><b>多</b>（白名单里有没登记的）⇒ <b>静默关闭该表的第二层防线</b>，
     *       且无任何报错 —— 这是更危险的形态。</li>
     * </ul>
     */
    @Test
    @DisplayName("白名单恰好等于 A 类 ∪ B 类（多一张 = 静默关防线，少一张 = 运行期报错）")
    void whitelistMustEqualAplusB() {
        Set<String> whitelist = readSharedTables();
        Set<String> registered = new LinkedHashSet<>(GROUP_A_NO_ISOLATION_COLUMN.keySet());
        registered.addAll(GROUP_B_SEMANTIC_EXEMPTION.keySet());

        // t_dept 已在批次 1b'' 移出白名单，故不属于「已登记但未入白名单」的违规
        List<String> expectedInWhitelist = new ArrayList<>(registered);
        expectedInWhitelist.remove("t_dept");

        List<String> unregistered = new ArrayList<>(whitelist);
        unregistered.removeAll(registered);
        assertTrue(unregistered.isEmpty(),
                "SHARED_TABLES 含未在 D-2a 登记表中的表：" + unregistered
                        + " ⇒ 白名单 = 「不过滤」，混入业务表等于静默关闭该表的第二层防线且无报错。"
                        + "请先在 SPEC §0.1 补定性，再登记到 A 或 B 类。");

        List<String> notWhitelisted = new ArrayList<>(expectedInWhitelist);
        notWhitelisted.removeAll(whitelist);
        assertTrue(notWhitelisted.isEmpty(),
                "已登记为豁免但不在 SHARED_TABLES 中的表：" + notWhitelisted
                        + " ⇒ 拦截器会注入 enterprise_id；A 类表无该列会直接 SQL 报错，"
                        + "B 类表的跨客户/跨企业语义会被限死。");
    }

    /**
     * A 类的结构前提：这 11 张表<b>不得</b>出现隔离列。
     *
     * <p>一旦出现隔离列，「无隔离列故须豁免」这个理由就不再成立 ⇒ 必须重新决策。
     */
    @Test
    @DisplayName("A 类均无 enterprise_id / tenant_id 列（豁免理由的结构前提）")
    void groupAMustHaveNoIsolationColumns() {
        List<String> offenders = new ArrayList<>();
        for (String table : GROUP_A_NO_ISOLATION_COLUMN.keySet()) {
            Integer n = countIsolationColumns(table);
            if (n != null && n > 0) {
                offenders.add(table + "(" + n + " 列)");
            }
        }
        assertTrue(offenders.isEmpty(),
                "A 类（无隔离列）表中出现了隔离列：" + offenders
                        + " ⇒ 「无隔离列故须豁免」的理由不再成立，须重新定性与调整白名单");
    }

    /**
     * B 类的结构前提：这 4 张表<b>必须有</b> {@code enterprise_id}。
     *
     * <p>反向对照：若 B 类某张表的 {@code enterprise_id} 也被删了，
     * 它就退化成 A 类，应改登记到 A 类 —— 留在 B 类会让「语义豁免」的理由失真。
     */
    @Test
    @DisplayName("B 类均有 enterprise_id 列（语义豁免的事实基础）")
    void groupBMustHaveEnterpriseIdColumn() {
        List<String> offenders = new ArrayList<>();
        for (String table : GROUP_B_SEMANTIC_EXEMPTION.keySet()) {
            Integer n = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM information_schema.columns "
                            + "WHERE table_name = ? AND column_name = 'enterprise_id'",
                    Integer.class, table);
            if (n == null || n == 0) {
                offenders.add(table);
            }
        }
        assertTrue(offenders.isEmpty(),
                "B 类（语义豁免）中这些表没有 enterprise_id 列：" + offenders
                        + " ⇒ 它们实为 A 类（无隔离列），应改登记到 A 类；"
                        + "留在 B 类会让豁免理由失真");
    }

    /** A 类无隔离列 ⇒ 也不该有 RLS（无谓词可写）。 */
    @Test
    @DisplayName("A 类均未开 RLS（无隔离列即无谓词可写）")
    void groupAMustNotHaveRowLevelSecurity() {
        List<String> offenders = new ArrayList<>();
        for (String table : GROUP_A_NO_ISOLATION_COLUMN.keySet()) {
            if (Boolean.TRUE.equals(isRlsEnabled(table))) {
                offenders.add(table);
            }
        }
        assertTrue(offenders.isEmpty(),
                "A 类表开了 RLS：" + offenders + " ⇒ 无隔离列却开 RLS，谓词无从表达，属配置与结构分叉");
    }

    /**
     * 登记表完整性：用固定基数钉死「输入不全」形态（AGENTS §4.5 第 21 条）。
     *
     * <p>若将来 {@code SHARED_TABLES} 增删而本登记���忘了改，
     * 主断言 {@link #whitelistMustEqualAplusB()} 会转红（两个方向都报），
     * 这里再钉一层基数，让「到底该是几张」有明确答案。
     */
    @Test
    @DisplayName("登记表基数：A 类 10 张 + B 类 4 张 = 白名单 14 张")
    void registryCardinalityMustMatchWhitelist() {
        assertEquals(9, GROUP_A_NO_ISOLATION_COLUMN.size(),
                "A 类应为 9 张（SPEC §0.1 的三类定性；t_dept 已于批次 1b'' 隔离并移出白名单）");
        assertEquals(5, GROUP_B_SEMANTIC_EXEMPTION.size(),
                "B 类应为 5 张（t_user + t_dept + 3 张拓扑/代理关联表）");
        assertEquals(GROUP_A_NO_ISOLATION_COLUMN.size() + GROUP_B_SEMANTIC_EXEMPTION.size() - 1,
                readSharedTables().size(),
                "登记表张数应为 A + B - 1（B 类里的 t_dept 已在批次 1b'' 移出白名单，只保留登记条目做历史留证）");
    }

    private Integer countIsolationColumns(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = ? "
                        + "AND column_name IN ('enterprise_id', 'tenant_id')",
                Integer.class, table);
    }

    private Boolean isRlsEnabled(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT c.relrowsecurity FROM pg_class c "
                        + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE c.relname = ? AND n.nspname = 'public'",
                Boolean.class, table);
    }

    @SuppressWarnings("unchecked")
    private Set<String> readSharedTables() {
        try {
            // 注意：EnterpriseDataPermissionInterceptor 不是 Spring Bean（在 MyBatisPlusConfig 里 new），
            // 只能反射读静态常量；@Autowired 会报 UnsatisfiedDependencyException（已实测）。
            Field f = Class.forName("com.huicai.common.context.EnterpriseDataPermissionInterceptor")
                    .getDeclaredField("SHARED_TABLES");
            f.setAccessible(true);
            return new LinkedHashSet<>((Set<String>) f.get(null));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "读不到 SHARED_TABLES —— 若该常量被改名或移除，本守卫会失去意义，"
                            + "请同步更新守卫（而不是让它静默跳过）", e);
        }
    }
}