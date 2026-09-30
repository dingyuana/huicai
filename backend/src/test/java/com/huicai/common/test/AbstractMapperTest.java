package com.huicai.common.test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.sme.cash.entity.BankAccountEntity;
import com.huicai.sme.cash.mapper.BankAccountMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mapper 层真实 DB 测试基类
 * 使用 Testcontainers 启动真实 PostgreSQL 16，Flyway 自动迁移
 * 每个测试方法独立事务，执行后自动回滚
 *
 * ⚠️ 属于慢测试（@SlowTest），需要 Docker 环境
 * 本地开发默认跳过，CI/CD 每晚执行全量
 *
 * 【容器生命周期设计（REQ-2026-108 / SPC-P99）】
 * 本基类的容器**不使用** {@code @Testcontainers} + {@code @Container} 组合，改为
 * 由静态初始化块显式 start()，原因是：
 *
 *   1. JUnit5 的 {@code @Testcontainers} 扩展在「多个测试类继承同一基类、
 *      forkCount=1 复用同一 JVM」时，会为每个测试类各创建并启动一个容器实例
 *      （实测 3 个类 → 3 个不同容器 ID / 3 个不同映射端口）；
 *   2. 而 Spring 的 TestContext 缓存是按配置 key 复用的 —— 由于
 *      {@code @DynamicPropertySource} 产出的 JDBC URL 在首个类建上下文时被固定，
 *      整个 JVM 只会创建一个 Spring 上下文；
 *   3. 两者叠加：第 2 个类起，容器端口已变，但缓存的上下文与 HikariPool 仍指向
 *      第 1 个容器的端口 → {@code Connection refused} /
 *      {@code HikariPool-1 - Connection is not available, request timed out after 30000ms}。
 *
 * 现象上「单类跑全绿、多类跑必红」，且 {@code -DreuseForks=false} 也无效
 * （每类各起一个容器反而更多，实测 131 → 194 次 ConnectionException）。
 *
 * 改为静态块 start() 后，整个 JVM 只存在一个容器实例、一个固定端口，
 * 上下文缓存与连接池始终指向同一端口，问题从根上消除。
 *
 * 容器回收：Testcontainers 创建时已打上 Ryuk 标签，JVM 退出时由 Ryuk 自动清理，
 * 不依赖 @Testcontainers 扩展的 afterAll。
 */
@SlowTest
@SpringBootTest
@Transactional
public abstract class AbstractMapperTest {

    protected static final PostgreSQLContainer<?> postgres;

    /**
     * 迁移后的数据库中 {@code t_bank_account} 为空表（无任何种子数据），
     * 而 {@code t_bank_statement.account_id} / {@code t_bank_journal.account_id}
     * 均有外键指向它。部分测试图省事直接用 enterpriseId 充当 accountId
     * （见 BankStatementDataIsolationTest#createStatement），必然触发
     * {@code violates foreign key constraint "fk_statement_account"}。
     * 子类应改用本方法取得合法账户 id。
     */
    @Autowired
    protected BankAccountMapper bankAccountMapper;

    private final Map<Long, Long> bankAccountIdCache = new ConcurrentHashMap<>();

    static {
        postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                .withDatabaseName("huicai_test")
                .withUsername("test")
                .withPassword("test");
        postgres.start();
    }

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        // 禁用 H2 console
        registry.add("spring.h2.console.enabled", () -> "false");
        // 确保 Flyway 自动迁移
        registry.add("spring.flyway.enabled", () -> "true");
    }

    /**
     * 为指定企业取一个合法的银行账户 id；若尚未创建则先创建。
     *
     * <p>同一企业重复调用返回同一 id（进程内缓存）。注意：测试方法带
     * {@code @Transactional}，方法级回滚会把本方法插入的账户一并回滚，
     * 而缓存不会回滚 —— 因此缓存仅在单个测试方法内可靠；跨方法请依赖
     * 「回滚后重新插入得到新 id，缓存指向已消失的 id」这一风险由
     * {@link #resetBankAccountCache()} 规避。
     */
    protected Long ensureBankAccount(Long enterpriseId) {
        Long cached = bankAccountIdCache.get(enterpriseId);
        if (cached != null) {
            return cached;
        }
        BankAccountEntity account = new BankAccountEntity();
        account.setAccountNo("TEST-ACC-" + enterpriseId + "-" + System.nanoTime());
        account.setAccountName("测试银行账户-" + enterpriseId);
        account.setBankName("测试银行");
        account.setEnterpriseId(enterpriseId);
        account.setBalance(java.math.BigDecimal.ZERO);
        account.setIsActive(true);
        bankAccountMapper.insert(account);
        bankAccountIdCache.put(enterpriseId, account.getId());
        return account.getId();
    }

    /**
     * 清空银行账户 id 缓存。测试方法回滚后账户行已消失，但缓存仍指向旧 id，
     * 故每个测试方法开始前应调用一次。
     */
    @org.junit.jupiter.api.BeforeEach
    void resetBankAccountCache() {
        bankAccountIdCache.clear();
    }

    /**
     * 取一个资产类别 id；迁移后 {@code t_asset_category} 为空表，而
     * {@code t_asset_card.category_id} 为 NOT NULL 外键，故需现造。
     */
    @Autowired
    protected com.huicai.sme.asset.mapper.AssetCategoryMapper assetCategoryMapper;

    private final Map<Long, Long> assetCategoryIdCache = new ConcurrentHashMap<>();

    protected Long ensureAssetCategory(Long enterpriseId) {
        Long cached = assetCategoryIdCache.get(enterpriseId);
        if (cached != null) {
            return cached;
        }
        com.huicai.sme.asset.entity.AssetCategoryEntity cat = new com.huicai.sme.asset.entity.AssetCategoryEntity();
        cat.setCode("TEST-CAT-" + enterpriseId);
        cat.setName("测试资产类别-" + enterpriseId);
        cat.setLevel(1);
        cat.setEnterpriseId(enterpriseId);
        cat.setUsefulLife(60);
        cat.setDepreciationMethod("STRAIGHT_LINE");
        assetCategoryMapper.insert(cat);
        assetCategoryIdCache.put(enterpriseId, cat.getId());
        return cat.getId();
    }

    @org.junit.jupiter.api.BeforeEach
    void resetAssetCategoryCache() {
        assetCategoryIdCache.clear();
    }

    /**
     * 取一个供应商 id。迁移后 {@code t_vendor} 可能为空表，而
     * {@code t_input_invoice.vendor_id} 带 FK 到 {@code t_vendor(id)}，
     * 勾稽查询又按 {@code vendor_id} 过滤，故需现造。
     */
    @Autowired
    protected com.huicai.base.masterdata.mapper.VendorMapper vendorMapper;

    private final Map<Long, Long> vendorIdCache = new ConcurrentHashMap<>();

    protected Long ensureVendor(Long enterpriseId) {
        Long cached = vendorIdCache.get(enterpriseId);
        if (cached != null) {
            return cached;
        }
        com.huicai.base.masterdata.entity.VendorEntity vendor =
                new com.huicai.base.masterdata.entity.VendorEntity();
        vendor.setCode("TEST-VENDOR-" + enterpriseId);
        vendor.setName("测试供应商-" + enterpriseId);
        vendor.setIsActive(true);
        vendor.setEnterpriseId(enterpriseId);
        vendorMapper.insert(vendor);
        vendorIdCache.put(enterpriseId, vendor.getId());
        return vendor.getId();
    }

    @org.junit.jupiter.api.BeforeEach
    void resetVendorCache() {
        vendorIdCache.clear();
    }

    /**
     * 取一个客户 id。迁移后 {@code t_customer} 为空表，而
     * {@code t_output_invoice.customer_id} 带 FK 到 {@code t_customer(id)}，
     * 故需现造（与 {@link #ensureVendor(Long)} 对称）。
     *
     * <p>{@code t_customer} 唯一约束为 {@code UNIQUE (code, enterprise_id)}，
     * 同一企业只造一行并缓存；每个测试方法前清缓存，因
     * {@code @Transactional} 回滚会使该行消失。
     */
    @Autowired
    protected com.huicai.base.masterdata.mapper.CustomerMapper customerMapper;

    private final Map<Long, Long> customerIdCache = new ConcurrentHashMap<>();

    protected Long ensureCustomer(Long enterpriseId) {
        Long cached = customerIdCache.get(enterpriseId);
        if (cached != null) {
            return cached;
        }
        com.huicai.base.masterdata.entity.CustomerEntity customer =
                new com.huicai.base.masterdata.entity.CustomerEntity();
        customer.setCode("TEST-CUSTOMER-" + enterpriseId);
        customer.setName("测试客户-" + enterpriseId);
        customer.setIsActive(true);
        customer.setEnterpriseId(enterpriseId);
        customerMapper.insert(customer);
        customerIdCache.put(enterpriseId, customer.getId());
        return customer.getId();
    }

    @org.junit.jupiter.api.BeforeEach
    void resetCustomerCache() {
        customerIdCache.clear();
    }

    /**
     * 取一个会计科目 id，不存在则创建。
     *
     * <p>{@code t_subject} 唯一约束为 {@code UNIQUE (code, enterprise_id)}，故按
     * {@code (code, enterprise_id)} 先查后建，重复调用返回同一 id。自动制证
     * （{@code AutoGenerationService}）按科目<b>编码</b>取科目，如
     * {@code 1002} 银行存款、{@code 2203}/{@code 1122} 应收/预收，
     * 缺失会 NPE，故需要这些科目的测试必须先建。
     *
     * @param enterpriseId 企业 ID
     * @param code         科目编码，如 {@code "1002"}
     * @param name         科目名称
     * @param direction    {@code debit} / {@code credit}
     * @return 科目 id
     */
    @Autowired
    protected com.huicai.base.system.mapper.SubjectMapper subjectMapper;

    private final Map<String, Long> subjectIdCache = new ConcurrentHashMap<>();

    protected Long ensureSubject(Long enterpriseId, String code, String name, String direction) {
        String key = enterpriseId + ":" + code;
        Long cached = subjectIdCache.get(key);
        if (cached != null) {
            return cached;
        }
        // 跨测试方法复用（关闭事务回滚的测试类会多次执行），故先查后建
        List<com.huicai.base.system.entity.Subject> existing = subjectMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.huicai.base.system.entity.Subject>()
                        .eq(com.huicai.base.system.entity.Subject::getCode, code)
                        .eq(com.huicai.base.system.entity.Subject::getEnterpriseId, enterpriseId)
                        .last("LIMIT 1"));
        if (!existing.isEmpty()) {
            subjectIdCache.put(key, existing.get(0).getId());
            return existing.get(0).getId();
        }
        com.huicai.base.system.entity.Subject subject = new com.huicai.base.system.entity.Subject();
        subject.setCode(code);
        subject.setName(name);
        subject.setLevel(1);
        subject.setDirection(direction);
        subject.setIsLeaf(true);
        subject.setIsActive(true);
        subject.setEnterpriseId(enterpriseId);
        subject.setDeleted(0);
        subjectMapper.insert(subject);
        subjectIdCache.put(key, subject.getId());
        return subject.getId();
    }

    @org.junit.jupiter.api.BeforeEach
    void resetSubjectCache() {
        subjectIdCache.clear();
    }

    // ==================== IDENTITY 序列对齐（REQ-2026-120 / 慢测 B 类）====================

    /**
     * 把所有 IDENTITY 序列推进到「≥ 该表当前 MAX(id)」。
     *
     * <h3>为什么必须做（慢测 B 类根因）</h3>
     * Flyway 的种子 migration 普遍用<b>显式 id</b> 插入基础数据
     * （如 {@code t_menu} 1~200、{@code t_subject} 1~102、{@code t_role}/{@code t_sys_config} 1~5），
     * 而 <b>从未调用过 nextval</b>，故对应序列仍停在起始值。测试首次让 DB 自行分配 id 时，
     * 拿到的正是 {@code nextval(seq) = 1}，与种子行<b>主键撞码</b>：
     * <pre>
     *   t_menu        MAX(id)=200  seq=1     ← 落后 199
     *   t_subject     MAX(id)=102  seq=18    ← 落后 84
     *   t_role        MAX(id)=5    seq=1
     *   t_sys_config  MAX(id)=5    seq=1
     * </pre>
     * 业务唯一键也会随之连锁失败：测试复用种子 id（如 role_id=1/menu_id=1），
     * 而该组合在 {@code uq_role_menu} / {@code uq_user_role} 中已存在。
     *
     * <h3>为何只需执行一次</h3>
     * PostgreSQL 的序列<b>不参与事务回滚</b>：即便测试方法因
     * {@code @Transactional} 回滚，行会消失但序列不会倒退。
     * 故每个 JVM 执行一次即可，之后序列只增不减，永远不会与既有行撞码。
     * 用 {@code AtomicBoolean} 保证只跑一次，避免 82 张表的 setval 反复执行。
     *
     * <p>注意 {@code setval(seq, n)} 的两参形式会把 {@code is_called} 置 true，
     * 于是下一次 {@code nextval} 返回 {@code n+1}，正是期望行为。
     */
    @Autowired
    protected org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private static final java.util.concurrent.atomic.AtomicBoolean SEQUENCES_ALIGNED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    @org.junit.jupiter.api.BeforeEach
    void alignIdentitySequencesOnce() {
        if (!SEQUENCES_ALIGNED.compareAndSet(false, true)) {
            return;
        }
        String query =
                "SELECT s.relname AS seq, t.relname AS tbl "
                        + "FROM pg_class s "
                        + "JOIN pg_namespace n ON n.oid = s.relnamespace "
                        + "JOIN pg_depend d ON d.objid = s.oid AND d.deptype = 'i' "
                        + "JOIN pg_class t ON t.oid = d.refobjid "
                        + "JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = d.refobjsubid "
                        + "WHERE n.nspname = 'public' AND a.attname = 'id' AND s.relkind = 'S'";
        for (java.util.Map<String, Object> row : jdbcTemplate.queryForList(query)) {
            String seq = (String) row.get("seq");
            String tbl = (String) row.get("tbl");
            // 表名/序列名均来自 pg_catalog，非外部输入
            jdbcTemplate.execute("SELECT setval('" + seq
                    + "', GREATEST((SELECT COALESCE(MAX(id), 1) FROM \"" + tbl + "\"), 1))");
        }
    }

    // ==================== RBAC 关联表造数（REQ-2026-120）====================

    /**
     * 新建一个角色并返回其 id。<b>不缓存</b> —— 关联表唯一键
     * （如 {@code uq_role_menu}）要求每次拿到全新 id 才能保证组合不与种子数据撞码。
     */
    @Autowired
    protected com.huicai.base.system.mapper.RoleMapper roleMapper;

    protected Long createRole(String codePrefix) {
        com.huicai.base.system.entity.RoleEntity role = new com.huicai.base.system.entity.RoleEntity();
        role.setCode(codePrefix + "-" + System.nanoTime());
        role.setName("测试角色-" + codePrefix);
        role.setStatus("ACTIVE");
        role.setDeleted(0);
        roleMapper.insert(role);
        return role.getId();
    }

    /**
     * 新建一个菜单并返回其 id。
     *
     * <p>此前本助手刻意绕过 {@code MenuEntity} 用 JDBC 插入，因为
     * {@code t_menu.menu_code} 是 NOT NULL 而 {@code MenuEntity} 缺 {@code menuCode} 字段
     * （REQ-2026-120 期间发现的 Entity↔DB 缺口）。该缺口已在
     * <b>REQ-2026-121</b> 通过补 Entity 字段修复，故此处已切回正常的 Mapper 路径。
     */
    protected Long createMenu(String codePrefix) {
        com.huicai.base.system.entity.MenuEntity menu = new com.huicai.base.system.entity.MenuEntity();
        menu.setName("测试菜单-" + codePrefix);
        menu.setMenuCode(codePrefix + "-" + System.nanoTime());
        menu.setType("MENU");
        menu.setSortOrder(1);
        menu.setIsActive(true);
        menu.setDeleted(0);
        menuMapper.insert(menu);
        return menu.getId();
    }

    @Autowired
    protected com.huicai.base.system.mapper.MenuMapper menuMapper;

    /** 新建一个用户并返回其 id。<b>不缓存</b>，理由同 {@link #createRole(String)}。 */
    protected Long createSysUser(String usernamePrefix) {
        com.huicai.base.system.entity.UserEntity user = new com.huicai.base.system.entity.UserEntity();
        user.setUsername(usernamePrefix + "-" + System.nanoTime());
        user.setPassword("encoded");
        user.setRealName("测试用户");
        user.setStatus("ACTIVE");     // chk_user_status
        user.setUserType("ENTERPRISE"); // chk_user_type
        user.setEnterpriseId(DEFAULT_ENTERPRISE_ID);
        user.setDeleted(0);
        userMapper.insert(user);
        return user.getId();
    }

    @Autowired
    protected com.huicai.base.system.mapper.UserMapper userMapper;

    // ==================== 企业上下文（REQ-2026-116 / 慢测 C 类）====================

    /**
     * 测试统一使用的企业 ID。绝大多数慢测都只关心单企业场景，
     * 且插入实体时往往不显式赋 {@code enterprise_id}。
     */
    protected static final Long DEFAULT_ENTERPRISE_ID = 1L;

    /**
     * 为每个测试方法设置默认企业上下文。
     *
     * <p><b>为什么必须在基类统一设置（慢测 C 类 90+ 项同根因）</b>：
     * V102~V105 迁移给 60+ 张表补了 {@code enterprise_id BIGINT NOT NULL} 且
     * <b>没有 DB 默认值</b>，而 {@code MyMetaObjectHandler.insertFill} 仅在
     * {@code EnterpriseContextHolder.get() != null} 时才回填该列。测试环境无
     * 登录态 → 上下文为 null → 不回填 → 报
     * {@code null value in column "enterprise_id" ... violates not-null constraint}。
     * 逐个测试类补 {@code setEnterpriseId()} 既重复又易漏。
     *
     * <p>副作用：{@code EnterpriseDataPermissionInterceptor} 会给 SELECT 注入
     * {@code enterprise_id = 1} 条件。**确实需要「无上下文放行」语义的用例
     * （如 {@code DataIsolationAuditTest} 的「漏洞确认」系列）必须在方法体内
     * 显式 {@code EnterpriseContextHolder.clear()}**，本基类的默认值不适用于它们。
     *
     * <p>子类 {@code @BeforeEach} 在本方法之后执行，故子类仍可覆盖为企业 B 等。
     */
    @org.junit.jupiter.api.BeforeEach
    void setDefaultEnterpriseContext() {
        EnterpriseContextHolder.set(DEFAULT_ENTERPRISE_ID);
    }

    /**
     * ThreadLocal 不随 {@code @Transactional} 回滚，必须在每个测试后清理，
     * 否则同一线程内的后续测试会继承上一个测试的企业上下文。
     */
    @org.junit.jupiter.api.AfterEach
    void clearEnterpriseContext() {
        EnterpriseContextHolder.clear();
    }

    /**
     * 把当前测试的企业上下文切换为指定企业。
     *
     * <p>供那些使用<b>独立企业 ID</b>（而非 {@link #DEFAULT_ENTERPRISE_ID}）造数的
     * 测试类调用：数据权限拦截器按上下文过滤，若上下文与造数用的
     * {@code enterprise_id} 不一致，查询会返回 0 行。
     * 子类 {@code @BeforeEach} 在 {@link #setDefaultEnterpriseContext()} 之后执行，
     * 因此调用本方法即可覆盖基类默认值。
     */
    protected void useEnterprise(Long enterpriseId) {
        EnterpriseContextHolder.set(enterpriseId);
    }

    /**
     * 在<b>无企业上下文</b>的状态下执行造数，用于测试跨租户隔离。
     *
     * <p><b>为何需要这个出口</b>：P102 / AT-102-7 起，
     * {@code MyMetaObjectHandler.insertFill} 对 {@code enterpriseId} 改为
     * <b>无条件覆盖</b>为上下文值（原先用 {@code strictInsertFill}，只在字段为
     * null 时才填，故请求体传 {@code {"enterpriseId": 999}} 即可直写他人租户）。
     * 于是 {@code entity.setEnterpriseId(2L)} + {@code mapper.insert(entity)}
     * 在上下文为企业 1 时<b>不再写 2</b>，跨租户隔离用例的前提会被静默改写。
     *
     * <p>该出口的实现正是生产代码里 {@code enterpriseId == null} 的那条分支
     * （无上下文 ⇒ 不干预入参值）。生产中任何 HTTP 写入路径都必经
     * {@code JwtAuthenticationFilter} 设置上下文，故该分支不可被外部请求触达；
     * 这一点由 {@code EnterpriseIdInjectionTest} 的两条前提守卫测试锁死。
     *
     * <p><b>使用约束</b>：仅限隔离/越权类测试造数，不得用于普通业务夹具 ——
     * 普通夹具本就该落在当前企业。
     */
    protected void withoutEnterpriseContext(Runnable action) {
        Long previous = EnterpriseContextHolder.get();
        EnterpriseContextHolder.clear();
        try {
            action.run();
        } finally {
            if (previous != null) {
                EnterpriseContextHolder.set(previous);
            }
        }
    }
}
