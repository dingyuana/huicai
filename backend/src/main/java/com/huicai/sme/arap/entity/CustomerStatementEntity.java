package com.huicai.sme.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_customer_statement")
public class CustomerStatementEntity extends BaseEntity {

    private Long customerId;
    /** 客户名 — 冗余字段，DB 无此列 */
    @TableField(exist = false)
    private String customerName;
    private String period;
    /** 报表日期 — 使用 period 字段替代，DB 无此列 */
    @TableField(exist = false)
    private LocalDate statementDate;

    /** 期初余额别名 — 使用 openingBalance，DB 无此列 */
    @TableField(exist = false)
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