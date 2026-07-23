package com.huicai.sme.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 坏账计提明细 — 记录每笔未清数据对应的计提金额
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_bad_debt_provision_detail")
public class BadDebtProvisionDetailEntity extends BaseEntity {

    /** 坏账准备主表ID */
    private Long provisionId;

    /** 数据来源类型: INVOICE_OUT / OTHER_RECEIVABLE / NOTE_RECEIVABLE / PREPAYMENT */
    private String sourceType;

    /** 数据来源单据ID */
    private Long sourceId;

    /** 客户ID */
    private Long customerId;

    /** 单据编号 */
    private String docNo;

    /** 到期日 */
    private LocalDate dueDate;

    /** 未清金额 */
    private BigDecimal unsettledAmount;

    /** 账龄区间 */
    private String agingBucket;

    /** 计提比例 */
    private BigDecimal ratio;

    /** 计提金额 */
    private BigDecimal provisionAmount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}