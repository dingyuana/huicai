package com.huicai.common.test;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.*;

/**
 * 慢测试标签 - 标记需要启动外部依赖的测试
 *
 * 包含：
 * - Testcontainers 数据库测试
 * - 需要真实 Redis/MinIO 的测试
 * - 需要其他外部服务的集成测试
 *
 * 执行方式：
 * mvn test -Dgroups="slow"      # 只跑慢测试
 * mvn test -Dgroups="!slow"     # 只跑快测试（默认）
 * mvn test                      # 跑全部测试
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag("slow")
public @interface SlowTest {
}
