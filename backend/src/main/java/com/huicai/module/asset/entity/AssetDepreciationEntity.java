package com.huicai.module.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_asset_depreciation")
public class AssetDepreciationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assetId;
    private String period;
    private BigDecimal depreciationAmount;
    private BigDecimal accumulatedDepreciation;
    private BigDecimal netValue;
    private Long voucherId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
