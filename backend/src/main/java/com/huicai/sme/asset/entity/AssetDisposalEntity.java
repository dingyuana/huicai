package com.huicai.sme.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_asset_disposal")
public class AssetDisposalEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String disposalNo;
    private Long assetId;
    private String disposalType;
    private LocalDate disposalDate;
    private String period;
    private BigDecimal originalValue;
    private BigDecimal accumulatedDepreciation;
    private BigDecimal netValue;
    private BigDecimal disposalIncome;
    private BigDecimal disposalExpense;
    private BigDecimal gainLoss;
    @StatusChangeable(entity = "ASSET_DISPOSAL", fieldName = "status")
    private String status;
    private Long voucherId;
    private Long createdBy;
    @TableField(exist = false)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 乐观锁版本号 */
    @Version
    @TableField("version")
    private Integer version;

    @TableLogic
    private Integer deleted;
}
