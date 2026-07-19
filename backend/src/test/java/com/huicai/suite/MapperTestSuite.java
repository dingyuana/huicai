package com.huicai.suite;

import com.huicai.base.system.mapper.SubjectMapperTest;
import com.huicai.sme.tax.mapper.OutputInvoiceMapperTest;
import com.huicai.base.voucher.mapper.VoucherMapperTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Mapper 真实 DB 测试套件
 *
 * 包含：所有核心业务 Mapper 的真实数据库测试
 *
 * 执行方式：
 * mvn test -Dtest=MapperTestSuite
 *
 * 预期耗时：3-5 分钟
 *
 * 可以发现 Mock 测试永远无法发现的问题：
 * - 数据库 check constraint 约束
 * - 外键关联约束
 * - 字段长度限制
 * - DECIMAL 精度丢失
 * - Flyway migration 脚本语法错误
 */
@Suite
@SuiteDisplayName("Mapper 真实 DB 测试套件")
@SelectClasses({
        SubjectMapperTest.class,
        OutputInvoiceMapperTest.class,
        VoucherMapperTest.class,
        // TODO: 新增 Mapper 测试后添加到这里
})
public class MapperTestSuite {
    // 不需要代码，由注解驱动
}
