package com.huicai.module.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_bank_journal")
public class BankJournalEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

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
