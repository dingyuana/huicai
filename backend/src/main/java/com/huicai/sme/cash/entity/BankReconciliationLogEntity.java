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

    // 🔴 SPEC-P153：此处曾有 `private Long enterpriseId;` —— Lombok 字段遮蔽了
    // BaseEntity 带 fill 的同名字段，MP 按子类裸字段建模 ⇒ INSERT 漏
    // enterprise_id 列 ⇒ 落 NULL（本表无 DB 默认值）⇒ RLS 谓词恒假
    // ⇒ 对账确认日志「写成功但永远查不到」（生产 0 行与此一致）。
    // 租户列一律由 BaseEntity 提供，子类不得重复声明。

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
