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
     * 子表是否按 {@code enterprise_id} 隔离。
     *
     * <p>🔴 <b>默认必须为 true</b>。若置 false 而子表<b>确实</b>有 {@code enterprise_id}，
     * 会导致「企业 B 的数据让企业 A 的记录永删不掉」——功能直接不可用（SPEC-P134 AT-134-6）。
     * 若子表<b>确实没有</b> {@code enterprise_id}，切面会按全局口径检查并打印一次性告警，
     * 该例外须在守卫测试中显式登记。</p>
     */
    boolean scopedByEnterprise() default true;
}