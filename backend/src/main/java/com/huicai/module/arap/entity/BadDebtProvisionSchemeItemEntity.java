package com.huicai.module.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 坏账计提方案明细 — 每个账龄区间对应的计提比例
 */
@Data
@TableName("t_bad_debt_provision_scheme_item")
public class BadDebtProvisionSchemeItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 方案ID */
    private Long schemeId;

    /** 账龄区间: current / days_0_30 / days_31_60 / days_61_90 / days_91_180 / days_181_365 / over_365 */
    private String bucketName;

    /** 计提比例(如 0.05 表示5%) */
    private BigDecimal ratio;

    /** 排序号 */
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}