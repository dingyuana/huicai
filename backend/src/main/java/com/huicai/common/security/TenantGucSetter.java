package com.huicai.common.security;

import com.huicai.common.context.EnterpriseContextHolder;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SPEC-P122：把 {@code app.enterprise_id} 写入「本事务真正使用的那条连接」。
 *
 * <p><b>为什么抽成独立类</b>：原先只有切面 {@link TenantRlsInitializer} 会设置 GUC，
 * 而切面只能拦到<b>自身声明</b>的 {@code @Transactional} 方法 ——
 * {@code IService#list(Wrapper)} 这类 MyBatis-Plus <b>default 接口方法</b>
 * （{@code ServiceImpl} 也不覆盖）不在覆盖范围内，实测 GUC 为 {@code null}，
 * 导致 RLS 静默返 0 行。切面与拦截器共用本类，保证两处行为一致、不重复实现。
 *
 * <p><b>为什么必须用 {@code DataSourceUtils}</b>：它返回的是<b>当前事务绑定的连接</b>，
 * 与 MyBatis 随后执行 SQL 所用的连接是同一条；用 {@code dataSource.getConnection()}
 * 会另开一条连接，GUC 设在别的连接上，谓词永远取不到值。
 *
 * <p><b>为什么用 {@code SET LOCAL}</b>：会话级 {@code SET} 会被连接池带到下一个请求，
 * 造成跨租户串数据；{@code SET LOCAL} 随事务结束自动失效。
 *
 * <p><b>事务内去重</b>：同一事务的多次查询只需设置一次。以「事务绑定的
 * {@link ConnectionHolder} 实例 + 企业号」作为缓存键，用事务资源而非 ThreadLocal 承载 ——
 * Spring 会在事务结束时自动清理，不会残留。
 */
public final class TenantGucSetter {

    private TenantGucSetter() {
    }

    /** 同一事务内已设置过的记录。 */
    private record Applied(ConnectionHolder holder, Long enterpriseId) {
    }

    private static String cacheKey(DataSource dataSource) {
        // 以数据源实例区分 key：同一 JVM 可能存在多个数据源（主库/只读库）
        return TenantGucSetter.class.getName() + ".applied@" + System.identityHashCode(dataSource);
    }

    /**
     * 若当前处于事务中且存在企业上下文，则在事务连接上设置 GUC。
     *
     * <p>三个前置条件缺一即不动作，且均为<b>fail-closed</b> 之外的正常场景：
     * 无事务（非 Spring 托管）时 {@code SET LOCAL} 本就不生效；
     * 无企业上下文（定时任务 / 初始化）时设假值反而会写错租户。
     */
    public static void apply(DataSource dataSource) {
        Long enterpriseId = EnterpriseContextHolder.get();
        if (enterpriseId == null || !TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }

        ConnectionHolder holder = currentHolder(dataSource);
        String key = cacheKey(dataSource);
        if (alreadyApplied(key, holder, enterpriseId)) {
            return;
        }

        Connection connection = DataSourceUtils.getConnection(dataSource);
        try (Statement statement = connection.createStatement()) {
            // enterpriseId 来自 EnterpriseContextHolder<Long>，非外部输入；
            // SET 不支持占位符，故显式转 long 消除注入面
            statement.execute("SET LOCAL app.enterprise_id = " + enterpriseId.longValue());
        } catch (SQLException e) {
            // fail-closed：设置失败意味着 RLS 谓词取不到值，查询将返 0 行。
            // 直接抛出并附带明确信息，避免变成「查不到数据」这种难定位的失败。
            throw new IllegalStateException(
                    "设置 app.enterprise_id 失败，RLS 将过滤全部数据（enterpriseId="
                            + enterpriseId + "）", e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }

        markApplied(key, holder, enterpriseId);
    }

    private static ConnectionHolder currentHolder(DataSource dataSource) {
        try {
            return (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
        } catch (IllegalStateException e) {
            return null;
        }
    }

    private static boolean alreadyApplied(String key, ConnectionHolder holder, Long enterpriseId) {
        // holder 为 null 时不信任缓存：无法证明「还是同一条连接」，
        // 宁可多设一次，也不能因缓存误判而漏设（漏设的后果是静默返 0 行）。
        if (holder == null) {
            return false;
        }
        try {
            Object cached = TransactionSynchronizationManager.getResource(key);
            return cached instanceof Applied applied
                    && applied.holder() == holder
                    && enterpriseId.equals(applied.enterpriseId());
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private static void markApplied(String key, ConnectionHolder holder, Long enterpriseId) {
        try {
            if (TransactionSynchronizationManager.hasResource(key)) {
                TransactionSynchronizationManager.unbindResource(key);
            }
            TransactionSynchronizationManager.bindResource(key, new Applied(holder, enterpriseId));
        } catch (IllegalStateException e) {
            // 资源同步不可用时放弃缓存；下次调用会重新设置（正确，只是略慢）
        }
    }
}