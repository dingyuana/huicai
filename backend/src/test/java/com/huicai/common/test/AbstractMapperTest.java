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
}
