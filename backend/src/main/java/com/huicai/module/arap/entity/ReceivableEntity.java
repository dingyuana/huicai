package com.huicai.module.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_receivable")
public class ReceivableEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long customerId;
    private Long docId;
    private Long voucherId;
    private String period;
    private LocalDate txDate;
    private BigDecimal amount;
    private BigDecimal settledAmount;
    private BigDecimal unsettledAmount;
    private LocalDate dueDate;
    private String summary;
    /** 状态: DRAFT/CONFIRMED/SETTLED/REVERSED，默认 CONFIRMED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 乐观锁版本号，MyBatis-Plus @Version 自动维护（防并发超核销） */
    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;
}
