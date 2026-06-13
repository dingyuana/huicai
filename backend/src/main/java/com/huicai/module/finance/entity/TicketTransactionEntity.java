package com.huicai.module.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 票据交易流水
 */
@Data
@TableName("t_ticket_transaction")
public class TicketTransactionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ticketId;

    private String transType;

    private LocalDate transDate;

    private String recipient;

    private BigDecimal amount;

    private String remark;

    private Long operatorId;

    private Long voucherId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}