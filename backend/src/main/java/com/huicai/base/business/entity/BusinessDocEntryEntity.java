package com.huicai.base.business.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_business_doc_entry")
public class BusinessDocEntryEntity extends BaseEntity {

    private Long docId;

    private String expenseType;

    private Long subjectId;

    private BigDecimal amount;

    private String invoiceNo;

    private String assistJson;

    private String summary;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
