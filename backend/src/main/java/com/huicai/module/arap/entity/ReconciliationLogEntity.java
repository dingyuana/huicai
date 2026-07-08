package com.huicai.module.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_reconciliation_log")
public class ReconciliationLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 来源单据类型: receipt / payment / bank_txn */
    private String sourceDocType;

    /** 来源单据 ID */
    private Long sourceDocId;

    /** 目标单据类型: INVOICE_OUT / INVOICE_IN */
    private String targetDocType;

    /** 目标发票 ID */
    private Long targetDocId;

    /** 核销金额 */
    private BigDecimal allocatedAmount;

    /** 现金折扣金额 */
    private BigDecimal discountAmount;

    /** 匹配度评分 */
    private BigDecimal matchScore;

    /** 匹配方式: AUTO / MANUAL */
    private String matchMethod;

    /** 状态: CONFIRMED / EXECUTED / REJECTED / CANCELLED */
    @StatusChangeable(entity = "RECONCILIATION_LOG", fieldName = "status")
    private String status;

    /** 操作类型: CREATE / CONFIRM / REJECT / CANCEL */
    private String operationType;

    /** 触发规则ID（自动核销时记录） */
    private String ruleId;

    /** 备注 */
    private String remark;

    /** 操作人 */
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
