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
@TableName("t_reconciliation_tolerance")
public class ReconciliationToleranceEntity extends BaseEntity {

    /** DB 无此列 */
    @TableField(exist = false)
    private Long tenantId;

    /** 客户/供应商ID（NULL表示全局配置） */
    private Long partyId;

    /** 方类型: CUSTOMER / VENDOR */
    private String partyType;

    /** DB tolerance_value: 容差金额阈值或百分比（默认5/10） */
    private BigDecimal toleranceValue;

    /** 容差类型: ABSOLUTE / PERCENT */
    private String toleranceType;

    /** 是否生效 */
    private Boolean isActive;

    /** 容差金额（业务展示字段，映射 toleranceValue 当 ABSOLUTE） */
    @TableField(exist = false)
    private BigDecimal toleranceAmount;

    /** 容差比例（业务展示字段，映射 toleranceValue 当 PERCENT） */
    @TableField(exist = false)
    private BigDecimal toleranceRate;

    @TableField
    private LocalDateTime createdAt;

    @TableField
    private LocalDateTime updatedAt;

    /** 逻辑删除: 0=正常 1=删除 */
    private Integer deleted;
}
