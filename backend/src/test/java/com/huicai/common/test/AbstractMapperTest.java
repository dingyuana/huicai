package com.huicai.common.test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

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

    static {
        postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                .withDatabaseName("huicai_test")
                .withUsername("test")
                .withPassword("test");
        postgres.start();
    }

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        // 禁用 H2 console
        registry.add("spring.h2.console.enabled", () -> "false");
        // 确保 Flyway 自动迁移
        registry.add("spring.flyway.enabled", () -> "true");
    }
}
