package com.huicai.module.arap.entity;

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

    private Long customerId;
    private Long docId;
    private String docNo;
    private BigDecimal unsettledAmount;
    private LocalDate dueDate;
    private Integer overdueDays;
    private String alertLevel;   // MILD / MODERATE / SEVERE / CRITICAL
    private String status;       // ACTIVE / DISMISSED / RESOLVED
    private LocalDateTime notifiedAt;
    private LocalDateTime dismissedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
