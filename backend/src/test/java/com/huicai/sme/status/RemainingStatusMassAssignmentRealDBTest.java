package com.huicai.sme.status;

import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.service.PeriodService;
import com.huicai.sme.budget.constant.BudgetStatus;
import com.huicai.sme.budget.entity.BudgetAdjustmentEntity;
import com.huicai.sme.budget.entity.BudgetEntity;
import com.huicai.sme.budget.service.BudgetService;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 门禁存量最后 3 处状态越权（REQ-2026-129 / P105）
 *
 * <p>由 {@code scripts/check_entity_status_massassignment.py} 检出，性质各异：
 * <ul>
 *   <li>{@code PeriodEntity.status} —— {@code chk_period_status} 用<b>小写</b>
 *       {@code open/closed/locked}；客户端可 POST {@code status=locked} 创建
 *       <b>已锁定的会计期间</b>，从而阻断该期间全部记账。</li>
 *   <li>{@code BudgetAdjustmentEntity.status} —— {@code t_budget_adjustment}
 *       <b>没有任何 status CHECK</b> ⇒ DB 零兜底，任意值可入库。</li>
 * </ul>
 *
 * <p>⚠️ **本类原先还包含 ArapSettlement 一条，已撤回**：实测无任何 Controller 直收
 * {@code ArapSettlementEntity}，越权面并不存在；而强制 DRAFT 反而破坏了
 * {@code ReconciliationServiceImpl#execute()}（人工提报直接以 SUBMITTED 建单），
 * 导致 4 个核销用例全红。**「加固」被内部调用方证伪，故撤回** ——
 * 教训：把不可经 REST 触发的路径当救火处理，可能伤害真实的内部调用方。
 *
 * <p>涉及的两个可达面：
 * </ul>
 */
@DisplayName("P105 门禁存量最后 3 处：期间 / 预算调整单 / 核销单")
class RemainingStatusMassAssignmentRealDBTest extends AbstractMapperTest {

    @Autowired
    private PeriodService periodService;

    @Autowired
    private BudgetService budgetService;

    @Nested
    @DisplayName("会计期间")
    class Period {

        /**
         * 注意：{@code PeriodServiceImpl} **覆写了 MyBatis-Plus 的 save()**，
         * periodCode/startDate/endDate 由服务层自动生成（AGENTS §4.3 第 15 条）。
         * 故夹具只给业务维度，编号与日期交给服务层 —— 直接调 Mapper.insert 会绕过全部生成逻辑。
         */
        private PeriodEntity draft() {
            PeriodEntity e = new PeriodEntity();
            int y = 2090 + (int) (Math.abs(System.nanoTime()) % 5);
            e.setYear(y);
            e.setMonth((int) (Math.abs(System.nanoTime() / 7) % 12) + 1);   // t_period_month_check 限 1..12
            e.setEnterpriseId(1L);
            return e;
        }

        @Test
        @DisplayName("不传 status 时落到默认 open")
        void defaultIsOpen() {
            PeriodEntity e = draft();
            e.setStatus(null);

            assertTrue(periodService.save(e), "保存失败");

            assertNotNull(e.getId(), "未回填 id");
            assertEquals("open", jdbcTemplate.queryForObject(
                    "SELECT status FROM t_period WHERE id = ?", String.class, e.getId()),
                    "默认值应由服务层决定");
        }

        @Test
        @DisplayName("负向：客户端传 status=locked 必须被忽略（否则创建出已锁定的会计期间）")
        void clientSuppliedLockedIsIgnored() {
            PeriodEntity e = draft();
            e.setStatus("locked");

            assertTrue(periodService.save(e), "保存失败");

            assertEquals("open", jdbcTemplate.queryForObject(
                    "SELECT status FROM t_period WHERE id = ?", String.class, e.getId()),
                    "chk_period_status 用**小写** open/closed/locked；客户端指定 locked "
                            + "会创建出已锁定的会计期间，阻断该期间全部记账");
        }

        @Test
        @DisplayName("负向：客户端传 deleted=1 必须被忽略（deleted 是服务端托管字段）")
        void clientSuppliedDeletedIsIgnored() {
            PeriodEntity e = draft();
            e.setDeleted(1);

            assertTrue(periodService.save(e), "保存失败");

            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT deleted FROM t_period WHERE id = ?", Integer.class, e.getId()),
                    "deleted 是服务端托管的逻辑删除标记，不接受客户端指定");
        }
    }

    @Nested
    @DisplayName("预算调整单")
    class Adjustment {

        private Long budgetId() {
            BudgetEntity b = new BudgetEntity();
            b.setBudgetNo("P105BA" + (System.nanoTime() % 1000000));
            b.setBudgetName("P105调整源预算");
            b.setPeriod("209912");
            b.setBudgetType("OVERALL");
            b.setTotalAmount(new BigDecimal("10000.00"));
            b.setEnterpriseId(1L);
            return budgetService.create(b, Collections.emptyList()).getId();
        }

        private BudgetAdjustmentEntity draft(Long budgetId) {
            BudgetAdjustmentEntity e = new BudgetAdjustmentEntity();
            e.setBudgetId(budgetId);
            e.setEnterpriseId(1L);
            return e;
        }

        @Test
        @DisplayName("不传 status 时落到 PENDING")
        void defaultIsPending() {
            BudgetAdjustmentEntity saved = budgetService.createAdjustment(draft(budgetId()));

            assertNotNull(saved.getId(), "未落库");
            assertEquals(BudgetStatus.ADJUSTMENT_PENDING, jdbcTemplate.queryForObject(
                    "SELECT status FROM t_budget_adjustment WHERE id = ?", String.class, saved.getId()),
                    "默认值应由服务层决定");
        }

        @Test
        @DisplayName("负向：客户端传任意 status 必须被忽略（该表无任何 CHECK，DB 零兜底）")
        void clientSuppliedStatusIsIgnored() {
            BudgetAdjustmentEntity e = draft(budgetId());
            e.setStatus("__GARBAGE__");

            BudgetAdjustmentEntity saved = budgetService.createAdjustment(e);

            assertEquals(BudgetStatus.ADJUSTMENT_PENDING, jdbcTemplate.queryForObject(
                    "SELECT status FROM t_budget_adjustment WHERE id = ?", String.class, saved.getId()),
                    "t_budget_adjustment 没有任何 status CHECK ⇒ DB 层零兜底，"
                            + "非法状态只能靠服务层挡住");
        }
    }
}
