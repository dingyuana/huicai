package com.huicai.sme.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_reconciliation_outstanding")
public class OutstandingItemEntity extends BaseEntity {

    private Long customerId;
    private Long statementId;

    private String outstandingType;
    private BigDecimal amount;
    private String description;
    private String evidence;

    private String status;

    private LocalDateTime resolvedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}