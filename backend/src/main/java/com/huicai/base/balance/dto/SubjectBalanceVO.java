package com.huicai.base.balance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 科目余额视图对象 — 含科目编码/名称/方向，供前端列表展示
 */
@Data
public class SubjectBalanceVO {

    private Long id;

    /** 科目ID */
    private Long subjectId;

    /** 科目编码 */
    private String subjectCode;

    /** 科目名称 */
    private String subjectName;

    /** 借贷方向: debit-借方, credit-贷方 */
    private String direction;

    /** 会计年度 */
    private Integer year;

    /** 会计期间(YYYYMM) */
    private String period;

    /** 期初余额 */
    private BigDecimal beginBalance;

    /** 本期借方发生额 */
    private BigDecimal debitTotal;

    /** 本期贷方发生额 */
    private BigDecimal creditTotal;

    /** 期末余额 */
    private BigDecimal endBalance;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
