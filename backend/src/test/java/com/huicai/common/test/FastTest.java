package com.huicai.common.test;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.*;

/**
 * 快测试标签 - 标记不需要外部依赖的测试
 *
 * 包含：
 * - Service 层 Mock 测试
 * - Controller 参数绑定测试
 * - 工具类测试
 * - 所有不需要数据库/Redis/外部服务的单元测试
 *
 * 这是默认执行的测试组，CI/CD 每次提交都会执行
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag("fast")
public @interface FastTest {
}
