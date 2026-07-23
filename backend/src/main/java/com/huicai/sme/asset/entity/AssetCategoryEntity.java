package com.huicai.sme.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_asset_category")
public class AssetCategoryEntity extends BaseEntity {

    private String code;
    private String name;
    private Long parentId;
    private Integer level;
    private String depreciationMethod;
    private Integer usefulLife;
    private BigDecimal residualRate;
    private Long assetSubjectId;
    private Long depreciationSubjectId;
    private Long expenseSubjectId;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
