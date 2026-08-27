package com.huicai.sme.tax.dto.vo;

import java.math.BigDecimal;

/**
 * P61 增值税附表二（进项）单行。
 * 按 vendorId + taxRate + declaredStatus 聚合，
 * isDeductible 依据 declaredStatus=DECLARED 判定（P57 口径）。
 */
public record AppendixIIRow(
        Long vendorId,
        String vendorName,
        BigDecimal amountExTax,          // 不含税金额
        BigDecimal taxAmount,             // 税额
        BigDecimal totalAmount,           // 含税金额
        BigDecimal rate,                  // 税率
        String certificationStatus,       // CERTIFIED / UNCONFIRMED（认证态）
        String declareStatus              // UNDECLARED / DECLARED（申报态）
) {
    public boolean isDeductible() {
        return "CERTIFIED".equals(certificationStatus) && "DECLARED".equals(declareStatus);
    }
}
