package com.huicai.module.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_reconciliation_outstanding")
public class OutstandingItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

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