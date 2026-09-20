package com.huicai.base.voucher.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 数量金额式账簿行视图对象（P2）
 */
@Data
public class QuantityAmountLedgerRowVO {

    /** 凭证号 */
    private String voucherNo;

    /** 凭证日期 */
    private String voucherDate;

    /** 科目编码 */
    private String subjectCode;

    /** 科目名称 */
    private String subjectName;

    /** 摘要 */
    private String summary;

    /** 借方数量 */
    private BigDecimal debitQuantity;

    /** 贷方数量 */
    private BigDecimal creditQuantity;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 借方金额 */
    private BigDecimal debitAmount;

    /** 贷方金额 */
    private BigDecimal creditAmount;

    /** 期末余额数量 */
    private BigDecimal closingQuantity;

    /** 期末余额金额 */
    private BigDecimal closingAmount;
}
