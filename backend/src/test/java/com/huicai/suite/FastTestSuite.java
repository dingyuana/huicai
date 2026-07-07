package com.huicai.suite;

import com.huicai.common.test.SlowTest;
import org.junit.platform.suite.api.ExcludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * 快测试套件 - 本地开发默认执行
 *
 * 包含：所有测试（@FastTest 或未标记的）
 * 排除：所有 @SlowTest 测试（需要外部依赖）
 *
 * 执行方式：
 * mvn test -Dtest=FastTestSuite
 *
 * 预期耗时：< 2 分钟
 */
@Suite
@SuiteDisplayName("快速测试套件 - 无外部依赖")
@SelectPackages("com.huicai")
@ExcludeTags("slow")
public class FastTestSuite {
    // 不需要代码，由注解驱动
}
