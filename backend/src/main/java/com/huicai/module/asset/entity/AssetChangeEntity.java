package com.huicai.module.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_asset_change")
public class AssetChangeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assetId;
    private String changeType;
    private String beforeValue;
    private String afterValue;
    private LocalDate changeDate;
    private Long voucherId;
    private String remark;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
