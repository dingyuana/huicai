package com.huicai.sme.cash.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 银行对账人工确认/驳回日志（REQ-2026-134 / P107 D4）。
 *
 * <p>银行侧原先无任何对账日志表，人工确认/驳回动作完全不可审计，
 * 违反铁律 #5（审计追踪）。本表补齐该缺口。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_bank_reconciliation_log")
public class BankReconciliationLogEntity extends BaseEntity {

    private Long statementId;
    private Long journalId;

    /** CONFIRM=人工确认匹配 / REJECT=人工驳回 */
    private String action;

    private String statusBefore;
    private String statusAfter;

    private String operator;
    private String remark;

    private Long enterpriseId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
