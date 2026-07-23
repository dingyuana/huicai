package com.huicai.sme.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 核销异常记录 — 存储自动核销失败/需要人工介入的异常.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_reconciliation_exception")
public class ReconciliationExceptionEntity extends BaseEntity {

    private Long tenantId;

    /** 来源单据类型: receipt / payment / bank_txn */
    private String sourceDocType;

    /** 来源单据 ID */
    private Long sourceDocId;

    /** 目标单据类型: INVOICE_OUT / INVOICE_IN (可能为空) — DB 无此列 */
    @TableField(exist = false)
    private String targetDocType;

    /** 目标单据 ID (可能为空) — DB 无此列 */
    @TableField(exist = false)
    private Long targetDocId;

    /** 客户/供应商 ID — DB 无此列 */
    @TableField(exist = false)
    private Long partyId;

    /** 客户/供应商类型: CUSTOMER / VENDOR — DB 无此列 */
    @TableField(exist = false)
    private String partyType;

    /** 来源金额 */
    private BigDecimal amount;

    /** 目标未结金额 — DB 无此列 */
    @TableField(exist = false)
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

    /** 异常原因描述 — DB 无此列 */
    @TableField(exist = false)
    private String exceptionReason;

    /** AI/规则推荐的匹配方案 (JSON) — DB 无此列 */
    @TableField(exist = false)
    private String matchSuggestion;

    /** 状态: OPEN / RESOLVED / IGNORED */
    @StatusChangeable(entity = "RECONCILIATION_EXCEPTION", fieldName = "status")
    private String status;

    /** 重试次数 — DB 无此列 */
    @TableField(exist = false)
    private Integer retryCount;

    /** 处理人 — DB 无此列 */
    @TableField(exist = false)
    private Long assignedTo;

    /** 处理人 */
    private Long resolvedBy;

    /** 处理时间 */
    private LocalDateTime resolvedAt;

    /** 备注 — DB 无此列 */
    @TableField(exist = false)
    private String remark;

    /** 创建人 — DB 无此列 */
    @TableField(exist = false)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 — DB 无此列 */
    @TableField(exist = false)
    private LocalDateTime updatedAt;
}