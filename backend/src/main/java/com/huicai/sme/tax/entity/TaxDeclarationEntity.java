package com.huicai.sme.tax.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_tax_declaration")
public class TaxDeclarationEntity extends BaseEntity {

    private String declarationNo;
    private String period;
    private String taxType;
    private java.time.LocalDate declaredDate;
    private BigDecimal payableAmount;
    @StatusChangeable(entity = "TAX_DECLARATION", fieldName = "status")
    private String status;
    private Long voucherId;
    private String remark;
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
