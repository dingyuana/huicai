package com.huicai.sme.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_aging_alert")
public class AgingAlertEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("party_id")
    private Long customerId;
    private Long docId;
    @TableField(exist = false)
    private String docNo;
    @TableField("amount")
    private BigDecimal unsettledAmount;
    private LocalDate dueDate;
    @TableField("days_overdue")
    private Integer overdueDays;
    private String alertLevel;
    private String status;
    @TableField(exist = false)
    private LocalDateTime notifiedAt;
    @TableField(exist = false)
    private LocalDateTime dismissedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
