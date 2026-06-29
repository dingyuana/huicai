package com.huicai.module.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_payable")
public class PayableEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long vendorId;
    private Long docId;
    private Long voucherId;

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

    /**
     * 发票编号（冗余存储，用于快速查询）
     */
    @TableField("invoice_no")
    private String invoiceNo;

    /**
     * 应付单编号
     */
    @TableField("payable_no")
    private String payableNo;

    private String period;
    private LocalDate txDate;
    private BigDecimal amount;
    private BigDecimal settledAmount;
    private BigDecimal unsettledAmount;
    private LocalDate dueDate;
    private String summary;
    /** 状态: DRAFT/CONFIRMED/SETTLED/REVERSED，默认 CONFIRMED */
    @StatusChangeable(entity = "PAYABLE", fieldName = "status")
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 乐观锁版本号，MyBatis-Plus @Version 自动维护（防并发超核销） */
    @Version
    private Integer version;

    /** 审核人ID */
    @TableField(exist = false)
    private Long auditedBy;

    /** 审核时间 */
    @TableField(exist = false)
    private LocalDateTime auditedAt;

    @TableLogic
    private Integer deleted;
}
