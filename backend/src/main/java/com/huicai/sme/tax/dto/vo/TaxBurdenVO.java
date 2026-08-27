package com.huicai.sme.tax.dto.vo;

import java.math.BigDecimal;

/**
 * P61 税负率分析。
 * payableTax = outputTax - inputDeduction（负数=留抵）
 * taxBurdenRate = payableTax / revenue（含税收入）；revenue=0 时返回 null
 */
public class TaxBurdenVO {
    private String period;
    private BigDecimal revenue;                // 含税销售收入（附表一 totalAmount）
    private BigDecimal outputTax;              // 销项税额
    private BigDecimal inputDeduction;         // 进项可抵扣税额
    private BigDecimal payableTax;             // 应纳增值税
    private BigDecimal taxBurdenRate;          // 税负率（可为 null）
    private BigDecimal yoyRate;                // 去年同期税负率（type=YOY 时）
    private BigDecimal yoyChange;              // 同比变动

    public TaxBurdenVO() {}

    public String getPeriod() { return period; }
    public void setPeriod(String p) { this.period = p; }
    public BigDecimal getRevenue() { return revenue; }
    public void setRevenue(BigDecimal v) { this.revenue = v; }
    public BigDecimal getOutputTax() { return outputTax; }
    public void setOutputTax(BigDecimal v) { this.outputTax = v; }
    public BigDecimal getInputDeduction() { return inputDeduction; }
    public void setInputDeduction(BigDecimal v) { this.inputDeduction = v; }
    public BigDecimal getPayableTax() { return payableTax; }
    public void setPayableTax(BigDecimal v) { this.payableTax = v; }
    public BigDecimal getTaxBurdenRate() { return taxBurdenRate; }
    public void setTaxBurdenRate(BigDecimal v) { this.taxBurdenRate = v; }
    public BigDecimal getYoyRate() { return yoyRate; }
    public void setYoyRate(BigDecimal v) { this.yoyRate = v; }
    public BigDecimal getYoyChange() { return yoyChange; }
    public void setYoyChange(BigDecimal v) { this.yoyChange = v; }
}
