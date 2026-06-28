package com.huicai.module.tax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_input_invoice")
public class InputInvoiceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String invoiceNo;
    private LocalDate invoiceDate;
    private String period;
    private Long vendorId;
    private String vendorName;
    private BigDecimal amount;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private String invoiceType;
    private String certificationStatus;
    private LocalDate certifiedDate;
    private String deductionPeriod;
    private BigDecimal deductionAmount;
    private Long voucherId;

    /**
     * 关联业务单据ID
     */
    @TableField("doc_id")
    private Long docId;

    /**
     * 业务单据编号（冗余存储，用于快速查询）
     */
    @TableField("doc_no")
    private String docNo;

    /**
     * 凭证编号（冗余存储，用于快速查询）
     */
    @TableField("voucher_no")
    private String voucherNo;

    private String remark;
    private Long createdBy;
    @TableField(exist = false)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 审核人ID */
    @TableField(exist = false)
    private Long auditedBy;

    /** 审核时间 */
    @TableField(exist = false)
    private LocalDateTime auditedAt;

    @TableLogic
    private Integer deleted;
}
