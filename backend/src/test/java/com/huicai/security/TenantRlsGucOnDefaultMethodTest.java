package com.huicai.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.service.PeriodService;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SPEC-P122：事务切面必须在「同一条事务连接」上把 {@code app.enterprise_id} 设进去。
 *
 * <p><b>为什么不能只在离线求值层面断言</b>：{@code @within} 是否命中，
 * 取决于 Spring AOP 对「被调用方法声明类型」的处理，静态求值只能证明切入点表达式，
 * 不能证明运行期真的执行了 {@code SET LOCAL}。本类因此走真实 Spring 上下文 + 真实连接，
 * 直接读回 GUC —— 即「看行为，不看属性」。
 *
 * <p><b>断言为何可靠</b>：测试方法自身带 {@code @Transactional}，与被测的
 * {@code periodService.list(...)} 共用同一条连接（{@code PROPAGATION_REQUIRED}），
 * 故 {@link JdbcTemplate} 读到的 {@code current_setting} 就是切面写入的那一份。
 *
 * <p><b>不依赖任何业务数据</b>：只断言 GUC 本身，不查表行数，避免开发库数据变动导致假红。
 *
 * <p><b>为何连接角色是超级用户也不影响本用例</b>：本类只断言「
 * {@code app.enterprise_id} 是否被写进这条连接」，而 {@code SET LOCAL} 与
 * {@code current_setting} 对超级用户同样有效 —— 超级用户只是让 RLS <b>不拦截行</b>，
 * 并不会让 GUC <b>不被设置</b>。断言对象是 GUC 本身，不是行数，故不受影响。
 *
 * <p><b>为何不用 H2</b>：H2 不支持 {@code SET LOCAL} 与
 * {@code current_setting(name, true)}，用它断言只会得到语法错误，而非业务结论
 * （实测踩过：{@code Syntax error in SQL statement "SET [*]LOCAL ..."}）。
 */
@DisplayName("SPEC-P122 事务切面须在真实连接上设置企业 GUC")
class TenantRlsGucOnDefaultMethodTest extends AbstractMapperTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PeriodService periodService;

    private String currentGuc() {
        return jdbcTemplate.queryForObject(
                "SELECT current_setting('app.enterprise_id', true)", String.class);
    }

    /**
     * 对照组：证明「真实上下文 + 真实事务连接 + 读回 GUC」这套装置本身有效。
     * 若此用例失败，说明装置或数据源有问题，不能据此解读另一条用例的结论。
     */
    @Test
    @DisplayName("对照：调用类内自身声明的方法时，切面应设上 GUC")
    void gucIsSetForSelfDeclaredMethod() {
        EnterpriseContextHolder.set(1L);
        try {
            periodService.getByPeriodCode("202407");
            assertEquals("1", currentGuc(),
                    "对照组失败：连自身声明的 @Transactional 方法都没设上 GUC，"
                            + "说明测试装置或事务连接有问题");
        } finally {
            EnterpriseContextHolder.clear();
        }
    }

    /**
     * 核心用例：{@code IService#list(Wrapper)} 是 MyBatis-Plus 的 <b>default 接口方法</b>，
     * {@code PeriodServiceImpl} 并未覆盖它。其声明类型 {@code IService} 不带
     * {@code @Transactional}，原切入点 {@code @within || @annotation} 因而不命中，
     * 导致 {@code GET /api/v1/periods/all} 静默返回空数组。
     */
    @Test
    @DisplayName("核心：调用 IService#list 这个 default 方法时，切面同样须设上 GUC")
    void gucIsSetForMybatisPlusDefaultMethod() {
        EnterpriseContextHolder.set(1L);
        try {
            periodService.list(new LambdaQueryWrapper<PeriodEntity>());
            assertEquals("1", currentGuc(),
                    "IService#list(Wrapper) 是 default 接口方法，声明在 IService 上；"
                            + "原切点按声明类型求值因而漏掉它，app.enterprise_id 未被设置，"
                            + "RLS 谓词取到 NULL → 所有租户表静默返 0 行");
        } finally {
            EnterpriseContextHolder.clear();
        }
    }
}