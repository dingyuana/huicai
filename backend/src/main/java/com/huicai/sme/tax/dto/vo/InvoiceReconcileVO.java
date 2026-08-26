package com.huicai.sme.tax.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * P58: 发票-收付款勾稽聚合视图（只读）.
 * 票流（认证/申报）+ 资金流（付款核销）三流合一展示。
 */
@Data
public class InvoiceReconcileVO {
    private Long invoiceId;
    private String invoiceNo;
    private LocalDate invoiceDate;
    private String vendorName;
    private String customerName;
    private BigDecimal amount;       // 价税合计
    private BigDecimal taxAmount;
    private String certificationStatus; // UNCERTIFIED / CERTIFIED
    private String declaredStatus;      // UNDECLARED / DECLARED (P57)
    private BigDecimal paidAmount;      // 已付款（来自业务单 settled_amount）
    private BigDecimal unpaidAmount;    // 未付款 = amount - paidAmount
    private String reconcileStatus;     // UNPAID / PARTIAL / PAID
    private Boolean hasRedFlushed;     // 是否已红冲
}
