package com.huicai.module.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 票据管理（支票/汇票）
 */
@Data
@TableName("t_ticket")
public class TicketEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ticketNo;

    private String ticketType;

    private BigDecimal amount;

    private Long bankId;

    private String payee;

    private String drawer;

    private LocalDate issueDate;

    private LocalDate expireDate;

    @StatusChangeable(entity = "TICKET", fieldName = "status")
    private String status;

    private String remark;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}