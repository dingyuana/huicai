package com.huicai.sme.cash.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_bank_account")
public class BankAccountEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String accountNo;
    private String accountName;
    private String bankName;
    private String currency;
    private Long subjectId;
    private BigDecimal balance;
    private Boolean isActive;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
