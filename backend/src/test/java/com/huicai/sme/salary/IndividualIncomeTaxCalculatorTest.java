package com.huicai.sme.salary;

import com.huicai.sme.salary.support.IndividualIncomeTaxCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P143（D-1）：累计预扣法个税计算器 —— 法定 7 档边界 + 速算扣除数 + 预扣口径。
 *
 * <p>税法常量不得漂移：每个边界档用「区间首尾」双点锁定
 * （税率档错位/速算数抄错在这两点上必然露馅）。</p>
 */
@DisplayName("SPEC-P143：个税累计预扣计算器 —— 7 档边界/速算扣除/预扣不为负")
class IndividualIncomeTaxCalculatorTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("空/负数累计应纳税所得额 → 0（不足起征）")
    void nullOrNegativeIsZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(IndividualIncomeTaxCalculator.taxOnCumulative(null)));
        assertEquals(0, BigDecimal.ZERO.compareTo(IndividualIncomeTaxCalculator.taxOnCumulative(bd("-12345.67"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(IndividualIncomeTaxCalculator.taxOnCumulative(BigDecimal.ZERO)));
    }

    @Test
    @DisplayName("第 1 档 3%：区间两端（0, 36000]——36000 应纳 1080")
    void bracket1() {
        assertEquals(0, bd("1080.00").compareTo(IndividualIncomeTaxCalculator.taxOnCumulative(bd("36000"))));
        assertEquals(0, bd("30.00").compareTo(IndividualIncomeTaxCalculator.taxOnCumulative(bd("1000"))));
    }

    @Test
    @DisplayName("第 2 档 10%/速算 2520：36000.01 起跳档连续（边界不跳变）")
    void bracket2() {
        assertEquals(0, bd("1080.00").compareTo(
                IndividualIncomeTaxCalculator.taxOnCumulative(bd("36000.01"))), "跨档瞬间税额应连续");
        assertEquals(0, bd("11880.00").compareTo(IndividualIncomeTaxCalculator.taxOnCumulative(bd("144000"))));
    }

    @Test
    @DisplayName("第 3 档 20%/速算 16920：边界连续 + 300000 处 43080")
    void bracket3() {
        assertEquals(0, bd("11880.00").compareTo(
                IndividualIncomeTaxCalculator.taxOnCumulative(bd("144000.02"))), "跨档连续性");
        assertEquals(0, bd("43080.00").compareTo(IndividualIncomeTaxCalculator.taxOnCumulative(bd("300000"))));
    }

    @Test
    @DisplayName("第 4 档 25%/速算 31920：420000 处 73080")
    void bracket4() {
        assertEquals(0, bd("73080.00").compareTo(IndividualIncomeTaxCalculator.taxOnCumulative(bd("420000"))));
    }

    @Test
    @DisplayName("第 5 档 30%/速算 52920：660000 处 145080")
    void bracket5() {
        assertEquals(0, bd("145080.00").compareTo(IndividualIncomeTaxCalculator.taxOnCumulative(bd("660000"))));
    }

    @Test
    @DisplayName("第 6 档 35%/速算 85920：960000 处 250080")
    void bracket6() {
        assertEquals(0, bd("250080.00").compareTo(IndividualIncomeTaxCalculator.taxOnCumulative(bd("960000"))));
    }

    @Test
    @DisplayName("第 7 档 45%/速算 181920：100 万处 268080")
    void bracket7() {
        assertEquals(0, bd("268080.00").compareTo(IndividualIncomeTaxCalculator.taxOnCumulative(bd("1000000"))));
    }

    @Test
    @DisplayName("本月应预扣 = 累计应纳 - 累计已预扣；负数封底为 0（不多退）")
    void monthlyWithholdingFloorAtZero() {
        assertEquals(0, bd("800.00").compareTo(IndividualIncomeTaxCalculator.monthlyWithholding(
                bd("1080.00"), bd("280.00"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(IndividualIncomeTaxCalculator.monthlyWithholding(
                bd("100.00"), bd("280.00"))), "累计已预扣超了本月累计应纳 → 本月 0，不得为负");
        assertEquals(0, bd("1080.00").compareTo(IndividualIncomeTaxCalculator.monthlyWithholding(
                bd("1080.00"), null)), "首月（无累计已预扣）按全额");
    }

    @Test
    @DisplayName("月减除费用常量 = 5000（税法值，勿改）")
    void monthlyDeductionConstant() {
        assertTrue(IndividualIncomeTaxCalculator.MONTHLY_STANDARD_DEDUCTION.compareTo(bd("5000")) == 0);
    }
}
