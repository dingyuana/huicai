package com.huicai.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SPEC-P134 —— 子表引用声明。
 *
 * <p>一个子表引用 = 一条<b>可 review、可 grep</b> 的声明。
 * 刻意<b>不</b>在运行时反射扫描 {@code pg_constraint} 自动生成：
 * 「表结构上的外键」与「业务上不可删的引用」<b>不是一回事</b> ——
 * 例如 {@code t_customer.subject_id} 上有外键，但客户档案被删除后，
 * 是否必须拦住取决于业务判断，不取决于数据库建没建约束。</p>
 *
 * @see ReferenceGuard
 */
@Documented
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface Ref {

    /** 子表名。 */
    String table();

    /** 子表中指向父表主键的列名。 */
    String column();

    /** 子表中文名，进入用户可见的错误消息（让用户知道该先处理什么）。 */
    String label();

    /**
     * 子表是否按 {@code enterprise_id} 过滤。
     *
     * <p>🔴 <b>默认 false（即不做租户过滤，全局检查）</b>，这是 2026-10-16 批次 3
     * 实测后<b>推翻</b>的原设计（原默认 true）。
     *
     * <p><b>为什么必须全局检查</b>：本检查回答的是「有没有任何行指向这个父记录」。
     * 一旦加上 {@code enterprise_id = 当前上下文} 过滤，就变成「<b>当前租户能不能看见</b>
     * 那些引用」，于是<b>过滤掉的那部分引用会被静默漏报</b> ——
     * 守卫会说「无引用，可以删」，而实际有引用。
     * 实测踩到：合同挂在<b>新建企业</b>下、检查按<b>上下文企业</b>过滤 ⇒ 查不到 ⇒
     * 守卫误放行。**这是 fail-open，方向完全错了。**
     *
     * <p><b>判据：完整性检查的过滤维度必须与「这条引用是否存在」一致，
     * 而不是与「调用者能否看见」一致。</b>后者是<b>读</b>权限，前者是<b>存在性</b>。
     *
     * <p>⚠️ 曾担心的「其它企业的数据会让本企业记录永删不掉」—— 那种情况
     * （跨租户引用同一条父记录）本身就是<b>已损坏的数据</b>，此时<b>应当</b>阻止删除。
     * 该担心是拿「读权限」的直觉去套「完整性」，方向反了。
     */
    boolean scopedByEnterprise() default false;
}