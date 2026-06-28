package com.huicai.common.test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Mapper 层真实 DB 测试基类
 * 使用 Testcontainers 启动真实 PostgreSQL 16，Flyway 自动迁移
 * 每个测试方法独立事务，执行后自动回滚
 *
 * ⚠️ 属于慢测试（@SlowTest），需要 Docker 环境
 * 本地开发默认跳过，CI/CD 每晚执行全量
 */
@SlowTest
@SpringBootTest
@Testcontainers
@Transactional
public abstract class AbstractMapperTest {

    @Container
    protected static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("huicai_test")
                    .withUsername("test")
                    .withPassword("test");

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
