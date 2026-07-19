package com.huicai.sme.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_asset_category")
public class AssetCategoryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

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
