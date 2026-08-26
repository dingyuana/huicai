package com.huicai.base.business.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import com.huicai.base.system.handler.JsonbTypeHandler;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_input_invoice")
public class InputInvoiceEntity extends BaseEntity {

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
     * 不含税金额（价税分离计算）— DB 无此列
     */
    @TableField(exist = false)
    private BigDecimal amountExTax;

    /**
     * AI 风险标签 — DB 无此列
     */
    @TableField(exist = false)
    private String aiRiskTag;

    /**
     * 处理状态: PENDING / PROCESSED / FAILED — DB 无此列
     */
    @TableField(exist = false)
    private String processStatus;

    /** 审核状态: PENDING_CONFIRM/PENDING_REVIEW/CONFIRMED/VOUCHERED/... */
    private String status;

    /** AI 科目映射推荐结果（JSONB）— DB 无此列 */
    @TableField(exist = false)
    private String aiMappingResult;

    private String invoiceType;
    private String certificationStatus;
    private LocalDate certifiedDate;
    private String deductionPeriod;
    private BigDecimal deductionAmount;
    private Long voucherId;

    /** 红冲原因: INVOICE_ERROR-开票有误, RETURN-退货, DISCOUNT-折让, OTHER-其他 (V138) */
    private String reverseReason;

    /** 原蓝字发票对应凭证ID（V138，红冲时快照） */
    private Long originalVoucherId;

    /** 原发票状态快照: CERTIFIED/UNCERTIFIED (V138) */
    private String originalCertificationStatus;

    /** 申报抵扣状态: UNDECLARED(已认证未申报)/DECLARED(已申报抵扣) (V139) */
    private String declaredStatus;

    /** 申报所属期 yyyyMM (V139) */
    private String declaredPeriod;

    /** 申报日期 (V139) */
    private LocalDate declaredDate;

    /** 被红冲的发票ID（红字发票→原蓝字）(V138) */
    private Long reversedFrom;

    /** 原蓝字发票号（V138） */
    @TableField("original_invoice_no")
    private String originalInvoiceNo;

    /**
     * 关联业务单据ID
     */
    @TableField("doc_id")
    private Long docId;

    /**
     * 业务单据编号（冗余存储，用于快速查询）— DB 无此列
     */
    @TableField(exist = false)
    private String docNo;

    /**
     * 凭证编号（冗余存储，用于快速查询）— DB 无此列
     */
    @TableField(exist = false)
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
