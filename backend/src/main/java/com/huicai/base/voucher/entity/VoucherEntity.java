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
     * 溯源单据ID（生成该凭证的原始单据ID）。
     * SPEC-P153：DB 无列时期红冲级联 cascadeReverseToSourceDocs / 数字穿透
     * 读回恒 null，「级联回写源单状态」对存量凭证从未生效。V194 补真实列。
     */
    @TableField("source_doc_id")
    private Long sourceDocId;

    /**
     * 溯源单据编号（冗余存储，用于快速查询）— V194 真实列
     */
    @TableField("source_doc_no")
    private String sourceDocNo;

    /**
     * 溯源单据类型：BUSINESS_DOC, OUTPUT_INVOICE, INPUT_INVOICE, RECEIVABLE, PAYABLE
     * — V194 真实列
     */
    @TableField("source_doc_type")
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

    /** 生成该凭证的模板ID (配置驱动, 替代硬编码) — V194 真实列 */
    @TableField("template_id")
    private Long templateId;

    /** 被红冲凭证ID */
    @TableField("reversed_from")
    private Long reversedFrom;

    /** 关联业务单据ID（P1 新增） */
    @TableField("business_doc_id")
    private Long businessDocId;

    /** 驳回原因 (P22) — V194 真实列（此前写了不落库，页端驳回意见恒空） */
    @TableField("rejected_reason")
    private String rejectedReason;

    /**
     * 红冲原因 — 保持幽灵字段（SPEC-P153 取证裁定，不建列）。
     * 🔴 全仓无写方/读方（InputInvoiceStateMachineServiceImpl L226 的
     * setReverseReason 接收者是 InputInvoiceEntity，扫描器同名片段误配）。
     * 若要启用红冲原因展示，写方与展示面须同批落地再转正。
     */
    @TableField(exist = false)
    private String reverseReason;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
