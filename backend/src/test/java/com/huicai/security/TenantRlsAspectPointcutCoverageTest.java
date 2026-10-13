package com.huicai.security;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.service.impl.PeriodServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P122：事务开启覆盖度的**离线**守卫。
 *
 * <p><b>背景（实测结论，勿凭印象推翻）</b>：Spring AOP 与事务拦截器求值
 * {@code @within} / {@code @annotation} 时，依据的是<b>被调用方法的声明类型</b>。
 * {@code IService#list(Wrapper)} 是 <b>default 接口方法</b>，
 * {@code ServiceImpl} 也不覆盖它 —— 因此 {@code PeriodServiceImpl} 的类级
 * {@code @Transactional} 对它<b>无效，事务不会开启</b>。
 *
 * <p><b>真实应用中的后果</b>：企业隔离 GUC 用 {@code SET LOCAL}，而
 * {@code SET LOCAL} 仅在事务块内有效 ⇒ GUC 设不上 ⇒ 非超级用户下 RLS 谓词取到
 * NULL ⇒ <b>静默返回 0 行、无任何报错</b>（实测 {@code GET /api/v1/periods/all}
 * 返回空数组）。真实行为断言见 {@code TenantRlsGucOnDefaultMethodTest}。
 *
 * <p><b>本类只守「离线可判定」的部分</b>：{@code @within} 对 default 方法不命中
 * 是既定事实，而修法是「让 ServiceImpl 显式覆盖并加方法级事务」。
 * 是否真的开了事务，必须由 {@code TenantRlsGucOnDefaultMethodTest} 在真实连接上验证 ——
 * 静态断言替代不了它（同 AGENTS §4.5「端到端断言可能对本次改动完全不敏感」）。
 */
@DisplayName("SPEC-P122 default 方法的事务覆盖不变量")
class TenantRlsAspectPointcutCoverageTest {

    private static final String EXPRESSION =
            "@annotation(org.springframework.transaction.annotation.Transactional) "
                    + "|| @within(org.springframework.transaction.annotation.Transactional)";

    private AspectJExpressionPointcut pointcut() {
        AspectJExpressionPointcut pc = new AspectJExpressionPointcut();
        pc.setExpression(EXPRESSION);
        return pc;
    }

    /**
     * 前提自证：若 MyBatis-Plus 日后把 {@code list} 改成具体方法，本类的不变量
     * 前提不再成立，须改写断言方式（守卫必须自证前提，否则会恒绿）。
     */
    @Test
    @DisplayName("前提：IService#list 确为 default 接口方法")
    void listIsDefaultInterfaceMethod() throws Exception {
        assertTrue(IService.class.getMethod("list", Wrapper.class).isDefault(),
                "前提失效：MyBatis-Plus 已将 IService#list 改为具体方法，本用例需重新评估");
    }

    /**
     * 对照组：证明求值装置有效。
     * 若此用例失败，说明是装置问题，不能据此解读下面那条「default 方法不命中」。
     */
    @Test
    @DisplayName("对照：类内自身声明的 @Transactional 方法会被切面命中")
    void aspectMatchesSelfDeclaredMethod() throws Exception {
        Method declared = PeriodServiceImpl.class.getMethod("getByPeriodCode", String.class);
        assertTrue(pointcut().matches(declared, PeriodServiceImpl.class),
                "求值装置无效：连类内自身声明的方法都不命中");
    }

/**
     * 反向记录既定事实：<b>未被覆盖</b>的 {@code IService} default 方法不被切面命中。
     *
     * <p>机理：Spring AOP 求值时先把接口方法解析到目标类的实现方法
     * （{@code AopUtils.getMostSpecificMethod}）。{@code PeriodServiceImpl} 一旦显式覆盖
     * 并加方法级 {@code @Transactional}，求值即命中；而仍停留在接口上的 default 方法
     * 不带该注解，因而不命中。
     *
     * <p>本用例取 {@code list(IPage, Wrapper)} —— 它<b>当前未被覆盖</b>，
     * 属已知债务（见 P122 遗留项）。一旦有人补上覆盖，本用例会转红，
     * 提醒复核该路径的事务与 GUC 覆盖面，而不是让行为变更静默发生。
     */
    @Test
    @DisplayName("既定事实：未被覆盖的 IService default 方法不被 @within 命中（前提锁）")
    void aspectDoesNotMatchUnoverriddenDefaultMethod() throws Exception {
        Method pagedList = IService.class.getMethod("list",
                com.baomidou.mybatisplus.core.metadata.IPage.class, Wrapper.class);
        assertTrue(!declaresItself(PeriodServiceImpl.class, pagedList),
                "PeriodServiceImpl 已覆盖 list(IPage, Wrapper)，本用例的前提已变化，请复核该路径的债务是否已消除");
        assertTrue(!pointcut().matches(pagedList, PeriodServiceImpl.class),
                "行为已变化：未覆盖的 IService default 方法现在也被命中，请复核拦截器兜底是否仍必要");
    }

    /** 目标类是否<b>自身声明</b>了给定签名（而非仅从接口继承）。 */
    private static boolean declaresItself(Class<?> type, Method inherited) {
        for (Method declared : type.getDeclaredMethods()) {
            if (declared.getName().equals(inherited.getName())
                    && java.util.Arrays.equals(declared.getParameterTypes(), inherited.getParameterTypes())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 核心不变量：凡走租户表、且实际调用 MP default 方法的入口，
     * 必须在本类中<b>显式覆盖</b>并加<b>方法级</b> {@code @Transactional}。
     */
    @Test
    @DisplayName("PeriodServiceImpl 必须显式覆盖 list(Wrapper) 并标注方法级事务")
    void periodServiceMustOverrideListWithTransactional() throws Exception {
        Method listMethod = PeriodServiceImpl.class.getMethod("list", Wrapper.class);
        assertTrue(listMethod.getDeclaringClass() == PeriodServiceImpl.class,
                "PeriodServiceImpl 未覆盖 IService#list(Wrapper)：它仍是 default 方法，"
                        + "类级 @Transactional 不会开启事务 ⇒ SET LOCAL 失效 ⇒ RLS 静默返 0 行");

        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(listMethod, Transactional.class);
        assertNotNull(transactional,
                "覆盖后的 list(Wrapper) 必须标注方法级 @Transactional，"
                        + "否则事务不开启，企业 GUC 仍设不上");
    }
}