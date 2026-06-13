package com.huicai.module.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_bad_debt_provision")
public class BadDebtProvisionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String period;
    private String method;
    private LocalDate provisionDate;
    private BigDecimal totalAmount;
    private Long voucherId;
    private String status;
    private String remark;
    private Long createdBy;
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
