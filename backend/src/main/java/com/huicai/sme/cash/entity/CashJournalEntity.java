package com.huicai.sme.cash.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 现金日记账
 */
@Data
@TableName("t_cash_journal")
public class CashJournalEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String period;

    private LocalDate journalDate;

    private String journalNo;

    private String summary;

    private BigDecimal debit;

    private BigDecimal credit;

    private BigDecimal balance;

    private Long subjectId;

    private Long oppositeSubjectId;

    private Long voucherId;

    private String source;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    @Version
    private Integer version;
}