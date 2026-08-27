package com.huicai.sme.tax.dto.vo;

import java.math.BigDecimal;
import java.util.List;

public class AppendixIIResponse {
    private String period;
    private BigDecimal totalAmountExTax;
    private BigDecimal totalTaxAmount;
    private BigDecimal deductibleTax;
    private List<AppendixIIRow> rows;

    public AppendixIIResponse() {}

    public AppendixIIResponse(String period, BigDecimal totalAmountExTax, BigDecimal totalTaxAmount, BigDecimal deductibleTax, List<AppendixIIRow> rows) {
        this.period = period;
        this.totalAmountExTax = totalAmountExTax;
        this.totalTaxAmount = totalTaxAmount;
        this.deductibleTax = deductibleTax;
        this.rows = rows;
    }

    public String getPeriod() { return period; }
    public void setPeriod(String p) { this.period = p; }
    public BigDecimal getTotalAmountExTax() { return totalAmountExTax; }
    public void setTotalAmountExTax(BigDecimal v) { this.totalAmountExTax = v; }
    public BigDecimal getTotalTaxAmount() { return totalTaxAmount; }
    public void setTotalTaxAmount(BigDecimal v) { this.totalTaxAmount = v; }
    public BigDecimal getDeductibleTax() { return deductibleTax; }
    public void setDeductibleTax(BigDecimal v) { this.deductibleTax = v; }
    public List<AppendixIIRow> getRows() { return rows; }
    public void setRows(List<AppendixIIRow> r) { this.rows = r; }
}
