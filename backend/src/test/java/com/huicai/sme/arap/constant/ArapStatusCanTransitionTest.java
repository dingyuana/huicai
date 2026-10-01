package com.huicai.sme.arap.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ArapStatus#canTransition} 单测（REQ-2026-136 / P105）
 *
 * <p><b>为何补这个测试</b>：该方法是 {@code t_arap_settlement} 声明状态机的**唯一权威图**，
 * 却<b>一个测试都没有</b> —— 图与实际行为脱节了很久都没人发现。
 *
 * <p><b>本类锁定的缺陷</b>：{@code reverse()} 的守卫是
 * {@code isSettlementReversible}（= CONFIRMED 或 VOUCHERED），
 * <b>不调 canTransition</b>；而 {@code canTransition} 的图里只有
 * {@code VOUCHERED -> REVERSED}，<b>缺 CONFIRMED -> REVERSED</b>。
 * 即生产里真的会发生 {@code CONFIRMED -> REVERSED}，而声明的图说它非法
 * —— <b>图不完整</b>（而非守卫过宽：反核销未制证的核销单本就是合法业务）。
 *
 * <p><b>不可把 null 当成合法迁移</b>：null 状态必须返回 false，
 * 否则「取不到实体就放行」会成为新的 fail-open。
 */
@DisplayName("P105 ArapStatus.canTransition 状态机图")
class ArapStatusCanTransitionTest {

    @Test
    @DisplayName("DRAFT 只能到 SUBMITTED 或 CANCELLED")
    void fromDraft() {
        assertTrue(ArapStatus.canTransition(ArapStatus.DRAFT, ArapStatus.SUBMITTED));
        assertTrue(ArapStatus.canTransition(ArapStatus.DRAFT, ArapStatus.CANCELLED));
        assertFalse(ArapStatus.canTransition(ArapStatus.DRAFT, ArapStatus.CONFIRMED),
                "DRAFT 不可直接确认 —— 必须先 submit（isApprovable 只认 SUBMITTED）");
        assertFalse(ArapStatus.canTransition(ArapStatus.DRAFT, ArapStatus.VOUCHERED));
        assertFalse(ArapStatus.canTransition(ArapStatus.DRAFT, ArapStatus.REVERSED));
    }

    @Test
    @DisplayName("SUBMITTED 只能到 CONFIRMED / REJECTED / CANCELLED")
    void fromSubmitted() {
        assertTrue(ArapStatus.canTransition(ArapStatus.SUBMITTED, ArapStatus.CONFIRMED));
        assertTrue(ArapStatus.canTransition(ArapStatus.SUBMITTED, ArapStatus.REJECTED));
        assertTrue(ArapStatus.canTransition(ArapStatus.SUBMITTED, ArapStatus.CANCELLED));
        assertFalse(ArapStatus.canTransition(ArapStatus.SUBMITTED, ArapStatus.DRAFT));
        assertFalse(ArapStatus.canTransition(ArapStatus.SUBMITTED, ArapStatus.VOUCHERED),
                "未确认不可制证");
    }

    @Test
    @DisplayName("CONFIRMED 可制证，也<b>可反核销</b>（反向修复项）")
    void fromConfirmed() {
        assertTrue(ArapStatus.canTransition(ArapStatus.CONFIRMED, ArapStatus.VOUCHERED));
        assertTrue(ArapStatus.canTransition(ArapStatus.CONFIRMED, ArapStatus.REVERSED),
                "CONFIRMED -> REVERSED 是生产真实路径：reverse() 守卫 isSettlementReversible "
                        + "放行 CONFIRMED，且异常文案明写「仅已确认或已记账的核销单可反核销」。"
                        + "原图缺这条边 ⇒ 图与实际行为不符");
        assertFalse(ArapStatus.canTransition(ArapStatus.CONFIRMED, ArapStatus.SUBMITTED),
                "已确认不得退回待审批");
        assertFalse(ArapStatus.canTransition(ArapStatus.CONFIRMED, ArapStatus.DRAFT));
        assertFalse(ArapStatus.canTransition(ArapStatus.CONFIRMED, ArapStatus.CANCELLED));
    }

    @Test
    @DisplayName("VOUCHERED 只能反核销")
    void fromVouchered() {
        assertTrue(ArapStatus.canTransition(ArapStatus.VOUCHERED, ArapStatus.REVERSED));
        assertFalse(ArapStatus.canTransition(ArapStatus.VOUCHERED, ArapStatus.VOUCHERED));
        assertFalse(ArapStatus.canTransition(ArapStatus.VOUCHERED, ArapStatus.CANCELLED));
    }

    @Test
    @DisplayName("终态 REJECTED / CANCELLED / REVERSED 无任何出边")
    void terminalStatesHaveNoOutgoing() {
        for (String terminal : new String[]{ArapStatus.REJECTED, ArapStatus.CANCELLED,
                ArapStatus.REVERSED}) {
            for (String to : new String[]{ArapStatus.DRAFT, ArapStatus.SUBMITTED,
                    ArapStatus.CONFIRMED, ArapStatus.VOUCHERED, ArapStatus.REVERSED,
                    ArapStatus.CANCELLED}) {
                assertFalse(ArapStatus.canTransition(terminal, to),
                        "终态 " + terminal + " 不得再流转到 " + to);
            }
        }
    }

    @Test
    @DisplayName("负向：null 或未定义状态一律拒绝（防 fail-open）")
    void nullAndUnknownAreRejected() {
        assertFalse(ArapStatus.canTransition(null, ArapStatus.CONFIRMED));
        assertFalse(ArapStatus.canTransition(ArapStatus.DRAFT, null));
        assertFalse(ArapStatus.canTransition(null, null));
        assertFalse(ArapStatus.canTransition("SETTLED", ArapStatus.REVERSED),
                "SETTLED 不在 chk_settlement_status 允许集内，不是本表合法起点");
    }

    @Test
    @DisplayName("isSettlementReversible 与 canTransition(→REVERSED) 必须一致")
    void guardPredicateAgreesWithGraph() {
        for (String from : new String[]{ArapStatus.DRAFT, ArapStatus.SUBMITTED,
                ArapStatus.CONFIRMED, ArapStatus.VOUCHERED, ArapStatus.REJECTED,
                ArapStatus.CANCELLED, ArapStatus.REVERSED}) {
            assertTrue(ArapStatus.isSettlementReversible(from)
                    == ArapStatus.canTransition(from, ArapStatus.REVERSED),
                    "守卫 isSettlementReversible 与状态机图对 " + from + " 的判定不一致 —— "
                    + "reverse() 用前者、契约声明后者，两者必须同源");
        }
    }
}