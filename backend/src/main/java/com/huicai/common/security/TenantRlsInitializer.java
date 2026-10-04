package com.huicai.common.security;

import com.huicai.common.context.EnterpriseContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * P102 / M5b：事务内设置 {@code app.enterprise_id}，让 RLS 第三层防线具备生效条件。
 *
 * <p><b>为什么必须做</b>：70 张表的策略均为
 * {@code enterprise_id = current_setting('app.enterprise_id', true)}，
 * 而全库 {@code set_config} 调用数为 <b>0</b> —— 第三层从未真正生效。
 * 实测（临时角色 + FORCE）：未设置时同一查询返回 <b>44</b> 行，
 * 设置为 1 后返回 <b>1</b> 行，证明谓词本身有效，缺的只是这个设置。
 *
 * <p><b>为什么用 {@code SET LOCAL} 而不是 {@code SET}</b>：后者是会话级的，
 * 连接池复用会把上一个请求的企业带给下一个请求，造成<b>跨租户串数据</b>；
 * {@code SET LOCAL} 随事务结束自动失效，是唯一正确选择。
 *
 * <p><b>挂载点</b>：拦截 {@code @Transactional} 方法最稳妥 —— 此时事务已开启，
 * {@link DataSourceUtils#getConnection} 拿到的是<b>该事务的连接</b>，
 * 设置随事务提交/回滚一并失效。放在 MyBatis 拦截器里则难以拿到同一连接。
 *
 * <p><b>上下文为 null 时不设置</b>：定时任务/系统初始化等无登录态路径本就不属于
 * 任何企业；此时若也去设一个假值反而会写错租户。RLS 在未设置时返 0 行，
 * 是策略的 fail-closed 默认行为，属预期。
 *
 * <p><b>顺序：必须在事务内执行（2026-10-03 实测教训）</b>：本切面与事务 advice
 * 若同为默认 order（{@code Ordered.LOWEST_PRECEDENCE}），谁在外层并不确定；
 * 实测落在事务外时 {@code SET LOCAL} 立刻失效（{@code pg_stat_activity} 里
 * {@code app.enterprise_id} 恒为 NULL），切到非超级用户后全部租户表读 0 行。
 * 现由 {@code TransactionAdviceOrderConfig} 把事务 advice 设为最外层来保证。
 *
 * <p><b>另一类漏设：方法根本没有 {@code @Transactional}</b>：实测
 * {@code SubjectServiceImpl#getTree}、{@code VoucherServiceImpl#pageQuery} 均无事务
 * （全库 75 个 {@code *ServiceImpl} 中 29 个一个 {@code @Transactional} 都没有），
 * 此时切面不触发。凡走租户表的读写路径都必须有事务。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class TenantRlsInitializer {

    private final DataSource dataSource;

    @Around("@annotation(org.springframework.transaction.annotation.Transactional) "
            + "|| @within(org.springframework.transaction.annotation.Transactional)")
    public Object applyTenantRls(ProceedingJoinPoint pjp) throws Throwable {
        setLocalEnterpriseId();
        return pjp.proceed();
    }

    private void setLocalEnterpriseId() {
        Long enterpriseId = EnterpriseContextHolder.get();
        if (enterpriseId == null) {
            return;
        }
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try (Statement statement = connection.createStatement()) {
            // enterpriseId 来自 EnterpriseContextHolder<Long>，非外部输入；
            // 仍用参数化不可行（SET 不支持占位符），故显式转 long 消除注入面
            statement.execute("SET LOCAL app.enterprise_id = " + enterpriseId.longValue());
        } catch (SQLException e) {
            // fail-closed：设置失败意味着 RLS 谓词取不到值，查询将返 0 行。
            // 此时若继续执行，业务会以「查不到数据」的形式失败，且原因难定位，
            // 故直接抛出并附带明确信息。
            throw new IllegalStateException(
                    "设置 app.enterprise_id 失败，RLS 将过滤全部数据（enterpriseId="
                            + enterpriseId + "）", e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
}