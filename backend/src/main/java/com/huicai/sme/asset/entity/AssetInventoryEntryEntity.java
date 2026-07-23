package com.huicai.sme.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_asset_inventory_entry")
public class AssetInventoryEntryEntity extends BaseEntity {

    private Long inventoryId;
    private Long assetId;
    private Integer bookQuantity;
    private Integer actualQuantity;
    private Integer diffQuantity;
    private String diffType;
    private BigDecimal diffAmount;
    private String remark;
}
