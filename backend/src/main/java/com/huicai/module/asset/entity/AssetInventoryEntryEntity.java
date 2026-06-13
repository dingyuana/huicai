package com.huicai.module.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("t_asset_inventory_entry")
public class AssetInventoryEntryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long inventoryId;
    private Long assetId;
    private Integer bookQuantity;
    private Integer actualQuantity;
    private Integer diffQuantity;
    private String diffType;
    private BigDecimal diffAmount;
    private String remark;
}
