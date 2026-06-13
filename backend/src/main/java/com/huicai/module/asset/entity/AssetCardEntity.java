package com.huicai.module.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_asset_card")
public class AssetCardEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String assetCode;
    private String assetName;
    private Long categoryId;
    private String spec;
    private Long deptId;
    private Long custodianId;
    private LocalDate acquisitionDate;
    private BigDecimal originalValue;
    private BigDecimal residualValue;
    private Integer usefulLife;
    private String depreciationMethod;
    private String status;
    private String location;
    private String serialNo;
    private String remark;
    private BigDecimal accumulatedDepreciation;
    private BigDecimal netValue;
    private String lastDepreciationPeriod;
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
