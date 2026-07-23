package com.huicai.base.voucher.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 凭证主表实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_voucher")
public class VoucherEntity extends BaseEntity {

    /** 凭证号(格式: 类型+年份+月份+流水号) */
    private String voucherNo;

    /** 会计期间(YYYYMM) */
    private String period;

    /** 凭证类型ID */
    private Long voucherTypeId;

    /** 状态: DRAFT-草稿, SUBMITTED-已提交, AUDITED-已审核, POSTED-已记账, CLOSED-已结账 */
    @StatusChangeable(entity = "VOUCHER", fieldName = "status")
    private String status;

    /** 借方总金额 */
    private BigDecimal totalDebit;

    /** 贷方总金额 */
    private BigDecimal totalCredit;

    /** 摘要 */
    private String summary;

    /** 来源: MANUAL-手工录入, TEMPLATE-模板生成, GENERATED-单据生成, REVERSAL-红冲 */
    private String source;

    /** 附件ID列表(逗号分隔) */
    private String attachmentIds;

    /**
     * 溯源单据ID（生成该凭证的原始单据ID）— DB 无此列
     */
    @TableField(exist = false)
    private Long sourceDocId;

    /**
     * 溯源单据编号（冗余存储，用于快速查询）— DB 无此列
     */
    @TableField(exist = false)
    private String sourceDocNo;

    /**
     * 溯源单据类型：BUSINESS_DOC, OUTPUT_INVOICE, INPUT_INVOICE, RECEIVABLE, PAYABLE
     * — DB 无此列
     */
    @TableField(exist = false)
    private String sourceDocType;

    /** 提交人 */
    private Long submittedBy;

    /** 提交时间 */
    private LocalDateTime submittedAt;

    /** 审核人 */
    private Long auditedBy;

    /** 审核时间 */
    private LocalDateTime auditedAt;

    /** 记账人 */
    private Long postedBy;

    /** 记账时间 */
    private LocalDateTime postedAt;

    /** 生成该凭证的模板ID (配置驱动, 替代硬编码) — DB 无此列 */
    @TableField(exist = false)
    private Long templateId;

    /** 被红冲凭证ID */
    @TableField("reversed_from")
    private Long reversedFrom;

    /** 关联业务单据ID（P1 新增） */
    @TableField("business_doc_id")
    private Long businessDocId;

    /** 驳回原因 (P22, 2026-06-22 新增) — DB 无此列 */
    @TableField(exist = false)
    private String rejectedReason;

    /** 红冲原因 (P22, 2026-06-22 新增) — DB 无此列 */
    @TableField(exist = false)
    private String reverseReason;
}
