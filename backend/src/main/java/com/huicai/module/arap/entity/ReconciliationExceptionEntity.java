package com.huicai.module.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 核销异常记录 — 存储自动核销失败/需要人工介入的异常.
 */
@Data
@TableName("t_reconciliation_exception")
public class ReconciliationExceptionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 来源单据类型: receipt / payment / bank_txn */
    private String sourceDocType;

    /** 来源单据 ID */
    private Long sourceDocId;

    /** 目标单据类型: INVOICE_OUT / INVOICE_IN (可能为空) */
    private String targetDocType;

    /** 目标单据 ID (可能为空) */
    private Long targetDocId;

    /** 客户/供应商 ID */
    private Long partyId;

    /** 客户/供应商类型: CUSTOMER / VENDOR */
    private String partyType;

    /** 来源金额 */
    private BigDecimal amount;

    /** 目标未结金额 */
    private BigDecimal unsettledAmount;

    /**
     * 异常类型:
     * PARTY_MISMATCH — 客商不匹配
     * AMOUNT_MISMATCH — 金额不匹配
     * INVOICE_NOT_FOUND — 找不到可核销发票
     * MATCH_FAILED — 匹配失败 (低于阈值)
     * APPROVAL_REQUIRED — 需人工审批
     */
    private String exceptionType;

    /** 异常原因描述 */
    private String exceptionReason;

    /** AI/规则推荐的匹配方案 (JSON) */
    private String matchSuggestion;

    /** 状态: OPEN / RESOLVED / IGNORED */
    private String status;

    /** 重试次数 */
    private Integer retryCount;

    /** 处理人 */
    private Long assignedTo;

    /** 处理人 */
    private Long resolvedBy;

    /** 处理时间 */
    private LocalDateTime resolvedAt;

    /** 备注 */
    private String remark;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
