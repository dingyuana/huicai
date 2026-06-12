package com.huicai.module.tax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_output_invoice")
public class OutputInvoiceEntity {

    @TableId(type = IdType.ASSIGN_ID)
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
    private String status;
    private Long docId;
    private Long voucherId;
    private String remark;
    private Long createdBy;
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
