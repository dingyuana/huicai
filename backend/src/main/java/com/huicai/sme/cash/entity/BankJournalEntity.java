package com.huicai.sme.cash.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_bank_journal")
public class BankJournalEntity extends BaseEntity {

    private Long accountId;
    private LocalDate txDate;
    private String period;
    private String txType;
    private String counterAccount;
    private BigDecimal amount;
    private String summary;
    private Long businessDocId;
    private Long voucherId;
    private Boolean isReconciled;

    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
