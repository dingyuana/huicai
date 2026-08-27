package com.huicai.sme.tax.dto.vo;

import java.math.BigDecimal;

/**
 * P61 增值税附表一（销项）单行。
 * 按 customerId + taxRate 聚合，用于对照税务局"增值税及附加税费申报表附列资料（一）"。
 */
public record AppendixIRow(
        Long customerId,
        String customerName,
        BigDecimal salesAmount,      // 不含税销售额
        BigDecimal taxAmount,         // 销项税额
        BigDecimal totalAmount,       // 含税金额
        BigDecimal rate              // 税率
) {}
