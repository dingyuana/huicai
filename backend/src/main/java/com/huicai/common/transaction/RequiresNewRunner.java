package com.huicai.common.transaction;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * 通用「独立事务执行器」：把传入动作放到 {@link Propagation#REQUIRES_NEW} 的新事务里运行。
 *
 * <p><b>为什么需要它（SPEC-P137）</b>：结转序列等「多步编排、单步失败不中断」的场景里，
 * 编排方法自身有事务（RLS 收口要求类级 {@code @Transactional}，见 AGENTS §4.5 第 58/61 条），
 * 而步内的业务服务（如 {@code DepreciationVoucherServiceImpl#generate}）标着默认 REQUIRED
 * ⇒ 加入外层事务。此时步内抛 {@code BusinessException} 会被内层事务拦截器把<b>共享事务</b>标记
 * rollback-only；编排层 {@code catch} 吞掉异常后，外层提交必然
 * {@code UnexpectedRollbackException}（2026-10-20 结账工作台 generate-sequence 500 实证）。
 *
 * <p><b>正解</b>：步骤经本 Runner 在独立事务中执行。步内异常只回滚该步<b>自己的</b>事务，
 * 异常抛出给调用方 catch 时该事务已终结，外层事务不受污染。
 *
 * <p><b>GUC 不缺位</b>：{@code TenantGucSetter} 由 {@code EnterpriseDataPermissionInterceptor}
 * 逐 SQL 触发，REQUIRES_NEW 的新连接（新 ConnectionHolder）未登记过 ⇒ 新事务首条 SQL 前
 * 会重新 {@code SET LOCAL app.enterprise_id}，RLS 谓词在本事务内正常取值。
 *
 * <p><b>提交时机</b>：动作正常返回即提交 —— 顺延步骤可见前序步已提交的行（幂等检查语义保留）。
 *
 * <p><b>代理语义</b>：本注解只在经 Spring 代理调用时生效（生产注入的是 CGLIB 代理）；
 * 单元测试若直接 {@code new RequiresNewRunner()} 则无代理、动作原地执行 ——
 * 这正是 L1 单测想要的等价行为（P137 §1.1 C）。
 */
@Component
public class RequiresNewRunner {

    /**
     * 在独立新事务中执行动作并返回其结果；异常时<b>回滚且仅回滚</b>本事务后原样抛出。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public <T> T run(Supplier<T> action) {
        return action.get();
    }
}
