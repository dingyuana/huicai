package com.huicai.sme.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_asset_depreciation")
public class AssetDepreciationEntity extends BaseEntity {

    private Long assetId;
    private String period;
    private BigDecimal depreciationAmount;
    private BigDecimal accumulatedDepreciation;
    private BigDecimal netValue;
    private Long voucherId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
