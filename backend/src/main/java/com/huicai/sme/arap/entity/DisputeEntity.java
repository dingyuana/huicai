package com.huicai.sme.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_reconciliation_dispute")
public class DisputeEntity extends BaseEntity {

    private Long statementId;
    private Long customerId;

    private String docNo;
    private String disputeType;

    private BigDecimal expectedAmount;
    private BigDecimal actualAmount;
    private BigDecimal diffAmount;

    private String reason;
    private String resolution;

    private Long resolvedBy;
    private LocalDateTime resolvedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}