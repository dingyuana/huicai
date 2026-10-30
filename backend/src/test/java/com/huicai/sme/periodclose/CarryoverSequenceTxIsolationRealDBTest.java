package com.huicai.sme.periodclose;

import com.huicai.base.voucher.dto.CarryoverStepResult;
import com.huicai.sme.periodclose.service.CarryoverSequenceService;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.common.transaction.RequiresNewRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P137 回归锁：结转序列「单步业务跳过」不得毒化外层事务。
 *
 * <p><b>缺陷机理（2026-10-20 生产 500 实证）</b>：
 * {@code CarryoverSequenceServiceImpl} 有类级 {@code @Transactional}（RLS 收口要求），
 * 步服务（{@code DepreciationVoucherServiceImpl#generate} 等）标默认 REQUIRED ——
 * 若步骤不经 REQUIRES_NEW 独立事务而直接在内层调用，步内抛
 * {@code BusinessException}（空账套「无可制证的折旧数据」）即把<b>共享事务</b>标记
 * rollback-only；{@code runStep} 吞掉异常转 SKIPPED 后，外层提交必然抛
 * {@code UnexpectedRollbackException} ⇒ generate-sequence 500。
 *
 * <p><b>为什么必须是真库 + 真代理</b>（AGENTS §4.5 第 0.4 节）：L1 的 Mockito 直调
 * impl 方法没有事务代理，rollback-only 机制根本不存在 ⇒ 只有「Spring 真代理 + 真库」
 * 才能复现与锁死该缺陷。
 *
 * <p><b>反证（P137 §1.3 场景 2，需手工注入）</b>：把 {@code RequiresNewRunner#run}
 * 的传播级别改回 REQUIRED，本用例必须转红（抛 UnexpectedRollbackException）；
 * 还原后恢复绿。
 *
 * <p><b>为什么类级是 NOT_SUPPORTED（§4.4 第 13 条形态，反证有效性的前提）</b>：
 * 默认的 @Transactional 测试方法在 teardown 时<b>回滚</b>而从不 commit ⇒ rollback-only
 * 标记随回滚一起蒸发 ⇒ 回归模式下用例也会假绿（首版反证实测：REQUIRED 下仍全绿）。
 * 置为 NOT_SUPPORTED 后无测试事务，{@code generateSequence} 自身的类级事务成为
 * <b>最外层</b>——回归模式下它 commit 时必然抛 UnexpectedRollbackException，与生产
 * （控制器无事务、服务为最外层）的爆炸形态严格一致。代价是无测试回滚保护，
 * 由动态企业号 + finally 显式清理兜底（本用例三步全 SKIPPED、零业务写入）。
 */
@DisplayName("SPEC-P137 守卫：单步业务跳过不得使 generate-sequence 500")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CarryoverSequenceTxIsolationRealDBTest extends AbstractMapperTest {

    @Autowired
    private CarryoverSequenceService carryoverSequenceService;

    @Autowired
    private RequiresNewRunner commitRunner;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 动态企业号：REQUIRES_NEW 内的写入会<b>真提交</b>（不受本类方法级回滚保护，
     * 见 SPEC-P137 §3 第 3 条），一次性企业号保证任何残留数据永不撞键。
     */
    private Long freshEnterprise() {
        return 990_700L + (System.nanoTime() % 900);
    }

    @Test
    @DisplayName("空账套生成序列: 正常返回 3 步且 DEPR=SKIPPED, 不得抛 UnexpectedRollbackException")
    void businessSkipMustNotPoisonOuterTransaction() {
        long ent = freshEnterprise();
        useEnterprise(ent);

        // ⚠️ 夹具必须「先落盘」（§4.4 第 13 条的镜像形态）：步服务在 REQUIRES_NEW
        //   独立事务里跑，看不到本测试方法事务里未提交的 INSERT —— 首版实跑 DEPR 步
        //   因看不到 t_period 行而报「期间 209801 未配置」，reason 失真。
        //   经 commitRunner（REQUIRES_NEW + 正常返回即提交）写入，步服务才能读到。
        commitRunner.run(() -> {
            jdbcTemplate.update(
                    "INSERT INTO t_period (year, month, period_code, start_date, end_date, "
                            + "enterprise_id, status, opening_status, created_at, updated_at, deleted) "
                            + "VALUES (2098, 1, '209801', DATE '2098-01-01', DATE '2098-01-31', "
                            + "?::bigint, 'open', 'none', NOW(), NOW(), 0) "
                            + "ON CONFLICT (period_code, enterprise_id) DO NOTHING",
                    ent);
            return null;
        });
        try {
            // 空账套：无计提折旧 ⇒ DEPR 必抛 BusinessException（业务跳过）——
            // 修复前该异常把共享事务标记 rollback-only，执行到此处必抛
            // UnexpectedRollbackException（生产 500 的等价形态）。
            List<CarryoverStepResult> results = assertDoesNotThrow(
                    () -> carryoverSequenceService.generateSequence("209801", 900_001L),
                    "单步业务跳过不得毒化外层事务（rollback-only → UnexpectedRollbackException）");

            assertEquals(3, results.size(), "序列必须返回全部三步（P84 契约）");

            CarryoverStepResult depr = results.get(0);
            assertEquals(CarryoverStepResult.STATUS_SKIPPED, depr.status(),
                    "空账套无折旧数据 ⇒ DEPR 应记 SKIPPED，实际 " + depr.status()
                            + "，reason=" + depr.reason());
            assertTrue(depr.reason() != null && depr.reason().contains("折旧"),
                    "DEPR 跳过原因应指向无折旧数据，实际 reason=" + depr.reason());

            for (CarryoverStepResult r : results) {
                assertNotEquals(CarryoverStepResult.STATUS_FAILED, r.status(),
                        "三步均不得以系统异常收场: " + r.step() + " reason=" + r.reason());
            }
        } finally {
            commitRunner.run(() -> {
                jdbcTemplate.update(
                        "DELETE FROM t_period WHERE period_code = '209801' AND enterprise_id = ?::bigint",
                        ent);
                return null;
            });
        }
    }
}
