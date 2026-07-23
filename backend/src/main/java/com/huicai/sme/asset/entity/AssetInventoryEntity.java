package com.huicai.sme.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_asset_inventory")
public class AssetInventoryEntity extends BaseEntity {

    private String inventoryNo;
    private LocalDate inventoryDate;
    private String period;
    @StatusChangeable(entity = "ASSET_INVENTORY", fieldName = "status")
    private String status;
    private Integer totalCount;
    private Integer profitCount;
    private Integer lossCount;
    private Long voucherId;
    private Long createdBy;
    @TableField(exist = false)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
