package com.huicai.base.voucher.dto.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 科目余额表行视图对象（T10-VO化，铁律#13）。
 * 字段名与 P1 阶段 Map key 完全一致，JSON 序列化结构不变，前端零改动。
 */
@Data
public class SubjectBalanceRowVO {

    /** 科目ID */
    private Long subjectId;

    /** 科目编码 */
    private String subjectCode;

    /** 科目名称 */
    private String subjectName;

    /** 借贷方向: debit-借方, credit-贷方 */
    private String direction;

    /** 期初余额 */
    private BigDecimal beginBalance;

    /** 本期借方发生额 */
    private BigDecimal debitTotal;

    /** 本期贷方发生额 */
    private BigDecimal creditTotal;

    /** 期末余额 */
    private BigDecimal endBalance;

    /** 年初余额（本年度最早期间快照期初，T4） */
    private BigDecimal yearBeginBalance;

    /** 本年累计借方发生额（T4） */
    private BigDecimal yearDebitTotal;

    /** 本年累计贷方发生额（T4） */
    private BigDecimal yearCreditTotal;
}