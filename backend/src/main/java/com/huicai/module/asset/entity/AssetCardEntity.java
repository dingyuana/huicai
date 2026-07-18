package com.huicai.module.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
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
    /**
     * 资产卡片状态.
     * <p>可选值：{@code DRAFT}(新增待确认), {@code IN_USE}(在用), {@code IDLE}(闲置, 原STOPPED), {@code DISPOSED}(已处置), {@code SCRAPPED}(已报废)</p>
     */
    @StatusChangeable(entity = "ASSET_CARD", fieldName = "status")
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
