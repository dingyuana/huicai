package com.huicai.base.report.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P96：{@link VoucherCashFlowEntity} 的值语义与字段自洽性。
 *
 * <p><b>为什么单独立一个类而不并入扫描式测试</b>：本轮实测该 Entity 覆盖率
 * {@code INSTRUCTION 0/277 / BRANCH 0/54 / METHOD 0/17} —— 它是 REQ-095 新建的表映射，
 * 此前无任何测试触碰。{@code EntityLombokBranchCoverageTest} 虽按类路径全量扫描，
 * 但那类测试只保证「分支被触碰」，**不保证字段与 DB 列一致**；而本类的真正风险是
 * 「Entity 少声明/多声明字段」——那是 {@code check-entity-schema.mjs} 与本类的分工。
 *
 * <p>本类只锁两件能独立验证的事：
 * <ol>
 *   <li><b>值语义</b>：{@code @Data} 生成的 equals/hashCode 覆盖全部字段
 *       （少字段参与 equals 会在集合去重时静默丢数据）</li>
 *   <li><b>金额可承载</b>：{@code amount} 是 {@link BigDecimal}，
 *       铁律 #7 禁止 double/float</li>
 * </ol>
 */
@DisplayName("P96 VoucherCashFlowEntity 值语义")
class VoucherCashFlowEntityTest {

    private static VoucherCashFlowEntity build() {
        VoucherCashFlowEntity e = new VoucherCashFlowEntity();
        e.setId(1L);
        e.setVoucherId(10L);
        e.setFlowType("OPERATING_IN");
        e.setAmount(new BigDecimal("100.00"));
        e.setEnterpriseId(2L);
        e.setCreatedAt(LocalDateTime.of(2026, 10, 8, 10, 0));
        return e;
    }

    @Test
    @DisplayName("全字段等值 ⇒ equals 为真且 hashCode 相同")
    void equalWhenAllFieldsEqual() {
        VoucherCashFlowEntity a = build();
        VoucherCashFlowEntity b = build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("逐字段变更均破坏相等（证明每个字段都参与 equals）")
    void everyFieldParticipatesInEquals() {
        VoucherCashFlowEntity base = build();

        VoucherCashFlowEntity idChanged = build();
        idChanged.setId(999L);
        assertNotEquals(base, idChanged, "id 必须参与 equals");

        VoucherCashFlowEntity voucherChanged = build();
        voucherChanged.setVoucherId(999L);
        assertNotEquals(base, voucherChanged, "voucherId 必须参与 equals");

        VoucherCashFlowEntity typeChanged = build();
        typeChanged.setFlowType("FINANCING_IN");
        assertNotEquals(base, typeChanged, "flowType 必须参与 equals");

        VoucherCashFlowEntity amountChanged = build();
        amountChanged.setAmount(new BigDecimal("100.01"));
        assertNotEquals(base, amountChanged, "amount 必须参与 equals");

        VoucherCashFlowEntity entChanged = build();
        entChanged.setEnterpriseId(999L);
        assertNotEquals(base, entChanged, "enterpriseId 必须参与 equals");

        VoucherCashFlowEntity createdChanged = build();
        createdChanged.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        assertNotEquals(base, createdChanged, "createdAt 必须参与 equals");
    }

    @Test
    @DisplayName("与 null / 其它类型比较为假（不得抛异常）")
    void compareWithNullAndForeignType() {
        VoucherCashFlowEntity e = build();
        assertNotEquals(null, e);
        assertNotEquals(new Object(), e);
        assertTrue(e.equals(e));
    }

    @Test
    @DisplayName("amount 为 BigDecimal（铁律 #7：禁止 double/float）")
    void amountIsBigDecimal() throws NoSuchFieldException {
        assertEquals(BigDecimal.class, VoucherCashFlowEntity.class.getDeclaredField("amount").getType());
    }

    @Test
    @DisplayName("flowType 取值属六大流量类型之一")
    void flowTypeIsOneOfSixKinds() {
        VoucherCashFlowEntity e = build();
        String[] legal = {"OPERATING_IN", "OPERATING_OUT", "INVESTING_IN",
                "INVESTING_OUT", "FINANCING_IN", "FINANCING_OUT"};
        for (String t : legal) {
            e.setFlowType(t);
            assertEquals(t, e.getFlowType());
        }
    }
}