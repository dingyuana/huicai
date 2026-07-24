package com.huicai.sme.cash.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_bank_account")
public class BankAccountEntity extends BaseEntity {

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

    /** t_bank_account 表没有 created_by/updated_by 列 */
    @TableField(exist = false)
    private Long createdBy;
    @TableField(exist = false)
    private Long updatedBy;

    @TableLogic
    private Integer deleted;
}
