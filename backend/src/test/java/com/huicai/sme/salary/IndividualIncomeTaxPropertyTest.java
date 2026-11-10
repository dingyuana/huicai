package com.huicai.sme.salary;

import com.huicai.sme.salary.support.IndividualIncomeTaxCalculator;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Label;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPEC-P154 切片三：个税计算器<b>属性测试</b>（jqwik，v4.0 §8 属性不变量落地）。
 *
 * <p>与守卫测试 {@code IndividualIncomeTaxCalculatorTest}（逐档双点，抓过 V1.107
 * 首版速算数多敲一个零）互补：双点验证「点位对」，属性验证「全域对」——</p>
 *
 * <ul>
 *   <li><b>属性 1（数学恒等，最强）</b>：速算法 ≡ 逐档累加。逐档累加是税法的
 *       定义式（每档内线性），速算扣除数正是该分段线性函数的闭式化简——两者必须
 *       逐点相等（±0.01 舍入）。速算数敲错任何一档，随机点几乎必命中该档 ⇒ 红。</li>
 *   <li><b>属性 2（单调性）</b>：应纳税所得额越大，累计应纳个税不减。</li>
 *   <li><b>属性 3（边界连续性）</b>：档位边界两侧 ±0.01 的税额差 &lt; 0.02——
 *       速算扣除数的设计目的就是消除边界跳变；跳档尖峰（速算数错）在此系统性显形。</li>
 *   <li><b>属性 4（累计预扣一致性）</b>：本月应预扣 = 累计税差且恒非负
 *       （多缴不退，本期不为负）。</li>
 *   <li><b>属性 5（全域非负 / 非正归零）</b>：任意输入（含负）税额 ≥ 0，非正输入 = 0。</li>
 * </ul>
 *
 * <p><b>oracle 独立性</b>：逐档累加 {@link #taxByBracketAccumulation} 是测试内独立
 * 实现（定义式直译），与被测速算法共享的只有税法常量（档位上界/税率）——不共享代码。
 * 纯函数毫秒级，jqwik 引擎与 JUnit 共存（pom 注释），计入 L1 基线。</p>
 */
@Label("SPEC-P154 切片三：个税累计预扣 —— 属性不变量（速算≡逐档/单调/边界连续/扣缴一致）")
class IndividualIncomeTaxPropertyTest {

    /** 税法常量（定义式 oracle 用，与被测类重复是刻意的——常量是外部事实非代码资产） */
    private static final BigDecimal[] UPPER = {
            new BigDecimal("36000"), new BigDecimal("144000"), new BigDecimal("300000"),
            new BigDecimal("420000"), new BigDecimal("660000"), new BigDecimal("960000")};
    private static final BigDecimal[] RATE = {
            new BigDecimal("0.03"), new BigDecimal("0.10"), new BigDecimal("0.20"),
            new BigDecimal("0.25"), new BigDecimal("0.30"), new BigDecimal("0.35"),
            new BigDecimal("0.45")};

    @Provide
    Arbitrary<BigDecimal> taxableIncome() {
        // 0.01 ~ 1,500,000，2 位小数——覆盖全 7 档（最高档 960,000+）
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.01"), new BigDecimal("1500000"))
                .ofScale(2);
    }

    @Provide
    Arbitrary<BigDecimal> anyAmount() {
        // 含负值全域（属性 5：非负不变量 + 非正归零）
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("-500000"), new BigDecimal("1500000"))
                .ofScale(2);
    }

    /** 属性 1：速算法 ≡ 逐档累加（数学恒等）。 */
    @Property(tries = 2000)
    void quickMethodEqualsBracketAccumulation(@ForAll("taxableIncome") BigDecimal t) {
        BigDecimal quick = IndividualIncomeTaxCalculator.taxOnCumulative(t);
        BigDecimal byBrackets = taxByBracketAccumulation(t);
        assertTrue(quick.subtract(byBrackets).abs().compareTo(new BigDecimal("0.01")) <= 0,
                "速算法与逐档累加在 t=" + t + " 处不等：速算=" + quick + "，逐档=" + byBrackets
                        + " —— 速算扣除数或档位边界有错");
    }

    /** 属性 2：单调非递减。 */
    @Property(tries = 1500)
    void taxIsMonotonic(@ForAll("taxableIncome") BigDecimal a, @ForAll("taxableIncome") BigDecimal b) {
        BigDecimal lo = a.min(b);
        BigDecimal hi = a.max(b);
        assertTrue(IndividualIncomeTaxCalculator.taxOnCumulative(lo)
                        .compareTo(IndividualIncomeTaxCalculator.taxOnCumulative(hi)) <= 0,
                "单调性违反：tax(" + lo + ") > tax(" + hi + ")");
    }

    /** 属性 3：档位边界连续性（速算扣除数的设计目的就是消除跳变）。 */
    @Property(tries = 12)
    void noBracketBoundarySpike(@ForAll("boundaries") BigDecimal boundary) {
        BigDecimal eps = new BigDecimal("0.01");
        BigDecimal before = IndividualIncomeTaxCalculator.taxOnCumulative(boundary.subtract(eps));
        BigDecimal after = IndividualIncomeTaxCalculator.taxOnCumulative(boundary.add(eps));
        assertTrue(after.subtract(before).abs().compareTo(new BigDecimal("0.02")) < 0,
                "档位边界 " + boundary + " 出现跳变尖峰：" + before + " → " + after
                        + " —— 速算扣除数与档位不匹配");
    }

    @Provide
    Arbitrary<BigDecimal> boundaries() {
        return Arbitraries.of(UPPER);
    }

    /** 属性 4：本月应预扣 = 累计税差（累计预扣法的核心定义）且恒非负。 */
    @Property(tries = 1500)
    void monthlyWithholdingEqualsCumulativeDifference(
            @ForAll("taxableIncome") BigDecimal a, @ForAll("taxableIncome") BigDecimal b) {
        BigDecimal lo = a.min(b);
        BigDecimal hi = a.max(b);
        BigDecimal taxLo = IndividualIncomeTaxCalculator.taxOnCumulative(lo);
        BigDecimal taxHi = IndividualIncomeTaxCalculator.taxOnCumulative(hi);
        BigDecimal monthly = IndividualIncomeTaxCalculator.monthlyWithholding(taxHi, taxLo);
        assertTrue(monthly.signum() >= 0, "本期应预扣不得为负：lo=" + lo + " hi=" + hi);
        assertTrue(taxLo.add(monthly).subtract(taxHi).abs()
                        .compareTo(new BigDecimal("0.01")) <= 0,
                "累计一致性违反：tax(lo)=" + taxLo + " + monthly=" + monthly
                        + " ≠ tax(hi)=" + taxHi);
    }

    /** 属性 5：全域非负；非正输入归零（不多退）。 */
    @Property(tries = 1000)
    void nonNegativeAndNonPositiveIsZero(@ForAll("anyAmount") BigDecimal t) {
        BigDecimal tax = IndividualIncomeTaxCalculator.taxOnCumulative(t);
        assertTrue(tax.signum() >= 0, "税额必须非负：" + t + " → " + tax);
        if (t.signum() <= 0) {
            assertTrue(tax.signum() == 0, "非正所得额必须归零（不多退）：" + t + " → " + tax);
        }
    }

    // ── oracle：逐档累加（税法定义式直译，与被测速算法不共享代码路径）──

    private BigDecimal taxByBracketAccumulation(BigDecimal t) {
        if (t.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal lower = BigDecimal.ZERO;
        for (int i = 0; i < RATE.length; i++) {
            boolean last = (i == RATE.length - 1);
            BigDecimal segTop = last ? t : UPPER[i].min(t);
            if (segTop.compareTo(lower) > 0) {
                tax = tax.add(segTop.subtract(lower).multiply(RATE[i]));
            }
            if (!last && t.compareTo(UPPER[i]) <= 0) {
                break;
            }
            lower = last ? null : UPPER[i];
        }
        return tax.setScale(2, RoundingMode.HALF_UP);
    }
}
