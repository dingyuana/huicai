package com.huicai.sme.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_customer_statement")
public class CustomerStatementEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long customerId;
    private String customerName;
    private String period;
    private LocalDate statementDate;

    private BigDecimal totalOriginal;
    /** 已结算金额 — DB 无此列 */
    @TableField(exist = false)
    private BigDecimal totalSettled;
    /** 未结算金额 — DB 无此列 */
    @TableField(exist = false)
    private BigDecimal totalUnsettled;

    private String status;

    private LocalDateTime sentAt;
    /** 确认时间 — DB 无此列 */
    @TableField(exist = false)
    private LocalDateTime confirmedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private Long createdBy;

    @TableLogic
    private Integer deleted;
}