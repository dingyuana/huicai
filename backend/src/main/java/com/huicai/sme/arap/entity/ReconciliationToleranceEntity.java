package com.huicai.sme.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_reconciliation_tolerance")
public class ReconciliationToleranceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 客户/供应商ID（NULL表示全局配置） */
    private Long partyId;

    /** 方类型: CUSTOMER / VENDOR */
    private String partyType;

    /** 容差金额阈值（默认5元） */
    @TableField(exist = false)
    private BigDecimal toleranceAmount;

    /** 容差比例阈值（默认10%） */
    @TableField(exist = false)
    private BigDecimal toleranceRate;

    /** 生效日期 */
    @TableField(exist = false)
    private LocalDate effectiveFrom;

    /** 失效日期 */
    @TableField(exist = false)
    private LocalDate effectiveTo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private Integer deleted;
}
