package com.huicai.sme.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_customer_statement")
public class CustomerStatementEntity extends BaseEntity {

    private Long customerId;
    /** 客户名 — 冗余字段，DB 无此列 */
    @TableField(exist = false)
    private String customerName;

    /**
     * 对账单编号 — DB `statement_no` 为 NOT NULL + UNIQUE（uq_customer_statement_no）。
     * REQ-2026-134 补齐：此前 Entity 无此字段，纯 MP insert 必然撞
     * "null value in column statement_no"，即对账单生成功能在真实 DB 上
     * 100% 不可用（AGENTS §4.2-13：NOT NULL 必填列在 Entity 完全缺失）。
     */
    private String statementNo;

    /** 账期起始日 — DB `period_start` NOT NULL，REQ-2026-134 补齐 */
    private LocalDate periodStart;
    /** 账期结束日 — DB `period_end` NOT NULL，REQ-2026-134 补齐 */
    private LocalDate periodEnd;

    private String period;
    /** 报表日期 — 使用 period 字段替代，DB 无此列 */
    @TableField(exist = false)
    private LocalDate statementDate;

    /** 期初余额 — DB `opening_balance` NOT NULL DEFAULT 0，REQ-2026-134 补齐 */
    private BigDecimal openingBalance;
    /** 期末余额 — DB `closing_balance` NOT NULL DEFAULT 0，REQ-2026-134 补齐 */
    private BigDecimal closingBalance;

    /** 期初余额别名 — 映射到 openingBalance（DB 无 total_original 列） */
    @TableField(exist = false)
    private BigDecimal totalOriginal;
    /** 已结算金额 — DB 无此列 */
    @TableField(exist = false)
    private BigDecimal totalSettled;
    /** 未结算金额 — DB 无此列 */
    @TableField(exist = false)
    private BigDecimal totalUnsettled;

    /** 备注 — DB `remark` 可空列 */
    private String remark;

    private String status;

    private LocalDateTime sentAt;
    /** 确认时间 — DB 无此列 */
    @TableField(exist = false)
    private LocalDateTime confirmedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private Long createdBy;

    @TableLogic
    private Integer deleted;
}