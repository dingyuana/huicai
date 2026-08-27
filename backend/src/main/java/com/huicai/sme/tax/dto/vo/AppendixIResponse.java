package com.huicai.sme.tax.dto.vo;

import java.util.List;

public class AppendixIResponse {
    private String period;
    private java.math.BigDecimal totalSalesAmount;
    private java.math.BigDecimal totalTaxAmount;
    private java.math.BigDecimal totalAmount;
    private List<AppendixIRow> rows;

    public AppendixIResponse() {}

    public AppendixIResponse(String period, java.math.BigDecimal totalSalesAmount, java.math.BigDecimal totalTaxAmount, java.math.BigDecimal totalAmount, List<AppendixIRow> rows) {
        this.period = period;
        this.totalSalesAmount = totalSalesAmount;
        this.totalTaxAmount = totalTaxAmount;
        this.totalAmount = totalAmount;
        this.rows = rows;
    }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public java.math.BigDecimal getTotalSalesAmount() { return totalSalesAmount; }
    public void setTotalSalesAmount(java.math.BigDecimal t) { this.totalSalesAmount = t; }
    public java.math.BigDecimal getTotalTaxAmount() { return totalTaxAmount; }
    public void setTotalTaxAmount(java.math.BigDecimal t) { this.totalTaxAmount = t; }
    public java.math.BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(java.math.BigDecimal t) { this.totalAmount = t; }
    public List<AppendixIRow> getRows() { return rows; }
    public void setRows(List<AppendixIRow> rows) { this.rows = rows; }
}
