package com.huicai.sme.tax.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 税负预警阈值配置（SPEC-P156 D-3）。
 * 每企业每税种一行；未配置时系统默认 3%（{@code DEFAULT_BURDEN_THRESHOLD}）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_tax_burden_config")
public class TaxBurdenConfigEntity extends BaseEntity {

    /** 系统默认预警阈值：3%（roadmap D-3） */
    public static final BigDecimal DEFAULT_BURDEN_THRESHOLD = new BigDecimal("0.03");

    /** 税种（VAT=增值税，扩展位） */
    private String taxType;

    /** 预警阈值，如 0.030000 = 3% */
    private BigDecimal thresholdRate;

    /** 是否启用预警 */
    private Boolean enabled;

    private String remark;
}
