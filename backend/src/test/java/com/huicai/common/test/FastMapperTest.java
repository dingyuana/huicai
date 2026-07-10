package com.huicai.common.test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mapper 层内存数据库测试基类
 * 使用 H2 内存数据库（PostgreSQL 兼容模式），无需 Docker/Testcontainers
 * 每个测试方法独立事务，执行后自动回滚
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class FastMapperTest {
    // Tests run fast without Docker — ideal for CI/CD
}
