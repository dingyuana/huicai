package com.huicai.module.tax.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_output_invoice")
public class OutputInvoiceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String invoiceNo;
    private LocalDate invoiceDate;
    private String period;
    private Long customerId;
    private String customerName;
    private BigDecimal amount;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private String invoiceType;
    /**
     * 状态: PENDING_CONFIRM / PENDING_REVIEW / CONFIRMED / VOUCHERED /
     *       FULLY_RECONCILED / PARTIALLY_RECONCILED / VOIDED / REVERSED
     * 详见 com.huicai.module.tax.constant.InvoiceStatus
     * 状态机详见 docs/specs/P21-sales-invoice-state-machine.md
     */
    @StatusChangeable(entity = "OUTPUT_INVOICE", fieldName = "status")
    private String status;
    private Long docId;
    private Long voucherId;
    private Long receivableId;
    private String remark;
    private Long createdBy;

    /**
     * 被哪张红字发票红冲（指向红字发票ID）
     */
    private Long reversedByInvoiceId;

    /**
     * 被哪张蓝字发票红冲（指向蓝字发票 ID，P36 新增）
     */
    private Long reversedFrom;

    /**
     * 原蓝字发票号码（红字发票专用）
     */
    private String originalInvoiceNo;

    /**
     * 原蓝字发票ID（红字发票专用，非数据库字段）
     */
    @TableField(exist = false)
    private Long originalInvoiceId;

    /**
     * 冲销此发票的红字发票号码（非数据库字段）
     */
    @TableField(exist = false)
    private String reversedByInvoiceNo;

    @TableField(exist = false)
    private Long updatedBy;

    /** 关联单据编号（冗余存储，用于快速查询） */
    @TableField("doc_no")
    private String docNo;

    /** 关联凭证编号（冗余存储，用于快速查询） */
    @TableField("voucher_no")
    private String voucherNo;

    /** 关联应收单编号（冗余存储，用于快速查询） */
    @TableField("receivable_no")
    private String receivableNo;

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

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;
}