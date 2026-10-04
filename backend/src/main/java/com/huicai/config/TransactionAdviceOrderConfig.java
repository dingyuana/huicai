package com.huicai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 事务 advice 顺序显式化（P102 / M5b，2026-10-03）。
 *
 * <p><b>为什么必须显式</b>：{@code TenantRlsInitializer} 用
 * {@code SET LOCAL app.enterprise_id} 让 RLS 生效，而该语句<b>只在事务内有效</b>
 * —— 落在自动提交的连接上会在语句结束时被数据库丢弃。切面与事务 advice 都用
 * {@code @Around}，若不指定顺序，二者默认都是 {@code Ordered.LOWEST_PRECEDENCE}，
 * 谁在外层<b>不确定</b>；实测（2026-10-03）切面落在事务外，导致
 * {@code pg_stat_activity} 里后端会话的 {@code app.enterprise_id} 恒为 NULL，
 * 切到非超级用户后<b>全部租户表读 0 行</b>。
 *
 * <p><b>修法</b>：把事务 advice 的 order 设为 0（高优先级 ⇒ 在最外层），
 * 切面保持默认的低优先级（{@code Ordered.LOWEST_PRECEDENCE}）⇒ 切面在事务内执行。
 *
 * <p><b>注意</b>：本类存在会使 Spring Boot 的
 * {@code TransactionAutoConfiguration.EnableTransactionManagementConfiguration} 退让，
 * 故必须把 Boot 原有的一项设置补齐：{@code proxyTargetClass = true}
 * （否则 CGLIB 代理失效，{@code @Transactional} 静默不生效 —— 又是一个假绿）。
 */
@Configuration
@EnableTransactionManagement(order = 0, proxyTargetClass = true)
public class TransactionAdviceOrderConfig {

    /** 显式暴露常量，便于 {@code TenantRlsInitializer} 注释引用与测试断言。 */
    public static final int TRANSACTION_ADVICE_ORDER = Ordered.HIGHEST_PRECEDENCE + 1;
}