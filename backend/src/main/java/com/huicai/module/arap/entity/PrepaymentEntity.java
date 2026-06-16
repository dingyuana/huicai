package com.huicai.module.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预付款/预收款实体
 * prepay_type = PAYMENT_PREPAY -> 供应商预付款
 * prepay_type = RECEIPT_PREPAY -> 客户预收款
 */
@Data
@TableName("t_prepayment")
public class PrepaymentEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** PAYMENT_PREPAY(供应商预付款) / RECEIPT_PREPAY(客户预收款) */
    private String prepayType;

    /** 供应商ID (PAYMENT_PREPAY 时使用) */
    private Long vendorId;

    /** 客户ID (RECEIPT_PREPAY 时使用) */
    private Long customerId;

    private Long docId;

    private Long voucherId;

    private String period;

    private LocalDate txDate;

    /** 总金额 */
    private BigDecimal amount;

    /** 已冲销金额 */
    private BigDecimal settledAmount;

    /** 未冲销金额 */
    private BigDecimal unsettledAmount;

    private String summary;

    /** DRAFT / CONFIRMED / SETTLED / CANCELLED */
    private String status;

    /** 关联单据类型 (bank_txn / MANUAL) */
    private String sourceDocType;

    private Long sourceDocId;

    private String remark;

    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
