package com.huicai.base.balance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 科目余额表实体
 */
@Data
@TableName("t_subject_balance")
public class SubjectBalanceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 科目ID */
    private Long subjectId;

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
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
