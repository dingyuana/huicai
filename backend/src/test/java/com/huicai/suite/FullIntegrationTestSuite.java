package com.huicai.suite;

import com.huicai.common.test.SlowTest;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * 慢测试套件 - CI/CD 每晚构建执行
 *
 * 包含：所有标记为 @SlowTest 的测试
 * - Testcontainers 数据库测试
 * - 完整链路集成测试
 *
 * 执行方式：
 * mvn test -Dtest=FullIntegrationTestSuite
 *
 * 预期耗时：10-30 分钟（取决于测试数量）
 *
 * 前置条件：Docker 环境可用
 */
@Suite
@SuiteDisplayName("完整集成测试套件 - 需要 Testcontainers")
@SelectPackages("com.huicai")
@IncludeTags("slow")
public class FullIntegrationTestSuite {
    // 不需要代码，由注解驱动
}
