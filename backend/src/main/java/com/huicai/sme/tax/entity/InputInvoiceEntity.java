package com.huicai.sme.tax.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import com.huicai.base.system.handler.JsonbTypeHandler;
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

    /**
     * 不含税金额（价税分离计算）
     */
    @TableField("amount_ex_tax")
    private BigDecimal amountExTax;

    /**
     * AI 风险标签
     */
    @TableField("ai_risk_tag")
    private String aiRiskTag;

    /**
     * 处理状态: PENDING / PROCESSED / FAILED
     */
    @TableField("process_status")
    private String processStatus;

    /** 审核状态: PENDING_CONFIRM/PENDING_REVIEW/CONFIRMED/VOUCHERED/... */
    private String status;

    /** AI 科目映射推荐结果（JSONB） */
    @TableField(value = "ai_mapping_result", typeHandler = JsonbTypeHandler.class)
    private String aiMappingResult;

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
    private Long auditedBy;

    /** 审核时间 */
    private LocalDateTime auditedAt;

    /** 审核驳回原因 */
    @TableField("reject_reason")
    private String rejectReason;

    @TableLogic
    private Integer deleted;
}
