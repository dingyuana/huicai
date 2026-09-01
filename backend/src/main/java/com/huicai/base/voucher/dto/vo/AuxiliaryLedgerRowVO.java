package com.huicai.base.voucher.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 辅助核算账行视图对象 — 按核算维度汇总某科目的期初/发生/期末余额
 */
@Data
public class AuxiliaryLedgerRowVO {

    /** 核算维度类型: customer/vendor/department/project/employee */
    private String dimensionType;

    /** 核算维度 ID */
    private Long dimensionValue;

    /** 核算维度名称（project 无实体时置 null） */
    private String dimensionName;

    /** 科目ID */
    private Long subjectId;

    /** 科目编码 */
    private String subjectCode;

    /** 科目名称 */
    private String subjectName;

    /** 借贷方向: debit-借方, credit-贷方 */
    private String direction;

    /** 期初余额（按历史维度分录聚合推算） */
    private BigDecimal beginBalance;

    /** 本期借方发生额 */
    private BigDecimal debitTotal;

    /** 本期贷方发生额 */
    private BigDecimal creditTotal;

    /** 期末余额 */
    private BigDecimal endBalance;
}
