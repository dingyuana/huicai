package com.huicai.module.tax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_tax_declaration")
public class TaxDeclarationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String declarationNo;
    private String period;
    private String taxType;
    private java.time.LocalDate declaredDate;
    private BigDecimal payableAmount;
    private String status;
    private Long voucherId;
    private String remark;
    private Long createdBy;
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
