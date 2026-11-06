package com.huicai.sme.salary.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * SPEC-P143（D-1）：个人所得税 —— 居民综合所得<b>累计预扣法</b>计算器（纯函数）。
 *
 * <p>法定 7 档年税率表（按<b>年</b>度累计预扣预缴应纳税所得额分档）+ 速算扣除数。
 * 本类不依赖任何 Spring/DB —— 档位边界与速算数是税法常量，守卫测试
 * {@code IndividualIncomeTaxCalculatorTest} 逐档锁定。</p>
 *
 * <h3>公式（个税法 · 累计预扣预缴）</h3>
 * <pre>
 * 累计预扣预缴应纳税所得额 = 累计计税应发 - 累计减除费用(5000×月数)
 *                          - 累计专项扣除(个人社保公积金) - 累计专项附加扣除 - 其他扣除
 * 本期应预扣 = (累计应纳税所得额 × 税率 - 速算扣除数) - 累计已预扣；负数 → 0（不多退）
 * </pre>
 */
public final class IndividualIncomeTaxCalculator {

    /** 每月减除费用（起征点 5000/月） */
    public static final BigDecimal MONTHLY_STANDARD_DEDUCTION = new BigDecimal("5000");

    private static final BigDecimal[] BRACKET_UPPER = {
            new BigDecimal("36000"), new BigDecimal("144000"), new BigDecimal("300000"),
            new BigDecimal("420000"), new BigDecimal("660000"), new BigDecimal("960000")};
    private static final BigDecimal[] RATES = {
            new BigDecimal("0.03"), new BigDecimal("0.10"), new BigDecimal("0.20"),
            new BigDecimal("0.25"), new BigDecimal("0.30"), new BigDecimal("0.35"),
            new BigDecimal("0.45")};
    // 法定速算扣除数（综合所得·按年）：0 / 2520 / 16920 / 31920 / 52920 / 85920 / 181920
    // 🔴 V1.107 首版整组多敲一个零（25200/169200/…），被本类守卫测试逐档双点锁出
    private static final BigDecimal[] QUICK_DEDUCTION = {
            BigDecimal.ZERO, new BigDecimal("2520"), new BigDecimal("16920"),
            new BigDecimal("31920"), new BigDecimal("52920"), new BigDecimal("85920"),
            new BigDecimal("181920")};

    private IndividualIncomeTaxCalculator() {
    }

    /**
     * 累计应纳税所得额对应的累计应纳个税。
     *
     * @param cumulativeTaxableIncome 累计预扣预缴应纳税所得额（可为负——负数按 0 计）
     * @return 累计应纳个税（2 位小数，HALF_UP）
     */
    public static BigDecimal taxOnCumulative(BigDecimal cumulativeTaxableIncome) {
        if (cumulativeTaxableIncome == null || cumulativeTaxableIncome.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        int bracket = RATES.length - 1;
        for (int i = 0; i < BRACKET_UPPER.length; i++) {
            if (cumulativeTaxableIncome.compareTo(BRACKET_UPPER[i]) <= 0) {
                bracket = i;
                break;
            }
        }
        return cumulativeTaxableIncome.multiply(RATES[bracket])
                .subtract(QUICK_DEDUCTION[bracket])
                .setScale(2, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO);
    }

    /**
     * 本月应预扣 = 累计应纳个税 - 累计已预扣（负数 → 0，扣多了下期抵减，本期不为负）。
     */
    public static BigDecimal monthlyWithholding(BigDecimal currentCumulativeTax, BigDecimal priorCumulativeWithheld) {
        BigDecimal prior = priorCumulativeWithheld == null ? BigDecimal.ZERO : priorCumulativeWithheld;
        return currentCumulativeTax.subtract(prior).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
