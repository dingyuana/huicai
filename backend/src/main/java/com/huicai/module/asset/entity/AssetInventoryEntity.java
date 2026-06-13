package com.huicai.module.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_asset_inventory")
public class AssetInventoryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String inventoryNo;
    private LocalDate inventoryDate;
    private String period;
    private String status;
    private Integer totalCount;
    private Integer profitCount;
    private Integer lossCount;
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
