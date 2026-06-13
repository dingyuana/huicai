package com.huicai.module.tax.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_tax_type")
public class TaxTypeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    private String name;
    private String taxCategory;
    private BigDecimal rate;
    private Boolean isActive;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
