package com.huicai.module.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_asset_disposal")
public class AssetDisposalEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String disposalNo;
    private Long assetId;
    private String disposalType;
    private LocalDate disposalDate;
    private String period;
    private BigDecimal originalValue;
    private BigDecimal accumulatedDepreciation;
    private BigDecimal netValue;
    private BigDecimal disposalIncome;
    private BigDecimal disposalExpense;
    private BigDecimal gainLoss;
    private String status;
    private Long voucherId;
    private Long createdBy;
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
