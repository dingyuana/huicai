package com.huicai.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SPEC-P134 —— 引用完整性守卫（声明式）。
 *
 * <p><b>为什么需要它</b>：外键约束只在父行被<b>物理删除</b>或父键被<b>更新</b>时触发，
 * 而铁律 #12 规定全部财务表<b>逻辑删除</b>，行永不物理删除
 * ⇒ <b>本项目所有外键的参照完整性保护结构性归零</b>。
 * 实测（2026-10-16）：删除被 45 条凭证分录引用的科目 {@code 1002 银行存款}
 * 返回 {@code 200 ok}，科目 {@code deleted=1}，45 条分录原样保留、零报错。</p>
 *
 * <p><b>判据沉淀</b>：裁定「DB 是否会替我兜底」时，查到外键存在<b>不等于</b>查到兜底有效，
 * 必须再问「<b>该表会不会被物理删除</b>」—— 本项目答案恒为「不会」。</p>
 *
 * <p><b>为何用切面而非逐点守卫</b>：25 张表同时满足「持有入向外键 + 有 {@code deleted} 列」，
 * 逐点加守卫必然漏（AGENTS §4.5 第 43 条）。</p>
 *
 * <p><b>已知局限</b>：Spring AOP 下同类内自调用（{@code this.xxx()}）绕过切面。
 * 故守卫测试除结构断言外，另有走 HTTP 代理的端到端用例证明切面真被触发。</p>
 *
 * @see Ref
 * @see ReferenceGuardAspect
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ReferenceGuard {

    /** 被删除的父表名，仅用于日志与错误消息。 */
    String parent();

    /** 父表的中文名，进入用户可见的错误消息。 */
    String label();

    /** 被引用检查的目标参数下标；默认取第一个参数（{@code delete(Long id)} 形态）。 */
    int idArgIndex() default 0;

    /** 子表引用声明。 */
    Ref[] children();
}