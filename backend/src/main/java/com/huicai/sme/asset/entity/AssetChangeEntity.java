package com.huicai.sme.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_asset_change")
public class AssetChangeEntity extends BaseEntity {

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
