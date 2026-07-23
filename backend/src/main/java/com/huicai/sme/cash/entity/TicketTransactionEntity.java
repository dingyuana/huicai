package com.huicai.sme.cash.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 票据交易流水
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_ticket_transaction")
public class TicketTransactionEntity extends BaseEntity {

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