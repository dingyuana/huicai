package com.huicai.module.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_business_doc_entry")
public class BusinessDocEntryEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

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
