package com.huicai.common.context;

import com.huicai.common.security.TenantGucSetter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.function.Supplier;

/**
 * SPEC-P161：参考库（enterprise_id=0）放行通道 —— 应用层上下文交换。
 *
 * <p><b>问题</b>：参考库是 ent0 数据（标准模板/科目，由 V115/V131 种子），而
 * 生产运行时身份 {@code huicai_app} 受 RLS 约束，谓词是
 * {@code enterprise_id = NULLIF(GUC)::bigint}。普通租户上下文（如 ent2）下，
 * 无论 JdbcTemplate 手写 {@code WHERE enterprise_id=0}（被 RLS 谓词叠加过滤）
 * 还是 MyBatis（被 {@link EnterpriseDataPermissionInterceptor} 注入
 * {@code enterprise_id=租户}），结果都是「参考库恒空」——
 * {@code /voucher-templates/reference-list} 与 {@code importFromReference}
 * 的静默失效同因。
 *
 * <p><b>修法</b>：不改 RLS 策略（零新迁移），把当前线程上下文临时交换为 0 并
 * 刷新 GUC —— GUC='0' 时标准谓词天然命中 ent0 行；退出时把上下文与 GUC 都
 * 还原回入口值。{@link TenantGucSetter} 的去重键是「最后一次 Applied
 * (holder, enterpriseId)」，ctx 2→0→2 双向 swap 均会真实重设，不受去重跳过影响。
 *
 * <p><b>Redis 式注意事项</b>（实现约束，调用方无需关心但 reviewer 需知）：
 * <ul>
 *   <li>只在<b>事务内</b>有意义（SET LOCAL 随事务结束自动失效，不会串池）；</li>
 *   <li>入口 ctx==null（无租户上下文）时还原后 GUC 可能残留 '0'：此时应用层
 *       过滤器本来就跳过（ctx null 不注入），残留 GUC 只会让 RLS 把本企业数据
 *       也滤掉——<b>fail-closed（看不见）而非 fail-open（串户）</b>，可接受；
 *       且交换段内可能执行了 apply(0)（SET LOCAL），只在原档案事务确实存在时发生。</li>
 *   <li>支持嵌套（controller 包 service）：还原值=入口值，嵌套无害。</li>
 * </ul>
 */
public final class ReferenceContextSwap {

    /** 参考库固定企业号（模板企业）。 */
    public static final long REFERENCE_ENTERPRISE_ID = 0L;

    private ReferenceContextSwap() {
    }

    /**
     * 在参考库上下文（ent0）中执行 {@code action}，执行前交换上下文+GUC，
     * {@code finally} 里还原。嵌套安全。
     */
    public static <T> T withReferenceLibrary(DataSource dataSource, Supplier<T> action) {
        Long original = EnterpriseContextHolder.get();
        try {
            EnterpriseContextHolder.set(REFERENCE_ENTERPRISE_ID);
            TenantGucSetter.apply(dataSource);
            return action.get();
        } finally {
            if (original == null) {
                EnterpriseContextHolder.clear();
            } else {
                EnterpriseContextHolder.set(original);
            }
            // ctx 非 null：重新 SET LOCAL 回原租户（去重键比对最后 Applied=0，必真实重设）；
            // ctx 为 null：apply 不动作——残留 GUC='0' 只会滤数据（fail-closed），不泄露。
            TenantGucSetter.apply(dataSource);
        }
    }

    /** 无返回值形态。 */
    public static void withReferenceLibrary(DataSource dataSource, Runnable action) {
        withReferenceLibrary(dataSource, () -> {
            action.run();
            return null;
        });
    }
}
