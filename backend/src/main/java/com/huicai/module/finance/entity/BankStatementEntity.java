package com.huicai.module.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_bank_statement")
public class BankStatementEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long accountId;
    private LocalDate txDate;
    private String txType;
    private String counterAccount;
    private BigDecimal amount;
    private String summary;
    private String externalNo;
    private String rawData;
    private Long matchedJournalId;
    private String matchStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime importedAt;

    @TableLogic
    private Integer deleted;
}
