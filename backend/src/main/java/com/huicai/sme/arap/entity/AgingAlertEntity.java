package com.huicai.sme.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 逾期预警（银行流水与业务单据混合数据源的逾期跟进表）
 *
 * <p><b>P110 Phase 2（2026-10-06）对齐修复</b>：旧映射严重偏离 DB：
 * ① 缺 doc_type/party_type（NOT NULL）⇒ generateAlerts 的 insert 必挂；
 * ② docNo/notifiedAt/dismissedAt 是 exist=false 野字段 ⇒ 别名 setter 赋值无效、读回恒 null；
 * ③ alertLevel 由 alertLevel() 产出 MILD/MODERATE/SEVERE，但 DB CHECK 只允许
 *    INFO/WARNING/CRITICAL ⇒ 与 insert 同步挂；已改为由服务直接产出 DB 允许值。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_aging_alert")
public class AgingAlertEntity extends BaseEntity {

    /** 来源单据（t_business_doc / t_prepayment 等，依 docType 解释） */
    private Long docId;

    /** 单据类型 INVOICE_OUT/OTHER_RECEIVABLE/NOTE_RECEIVABLE/PREPAYMENT ... (DB: doc_type) */
    @TableField("doc_type")
    private String docType;

    /** 往来方 ID（客户/供应商）—— DB 列名为 party_id，继承 BaseEntity 无此字段，故在此显式声明 */
    @TableField("party_id")
    private Long customerId;

    /** CUSTOMER/VENDOR (DB: party_type) */
    @TableField("party_type")
    private String partyType;

    /** 到期日 */
    private LocalDate dueDate;

    /** 逾期天数 */
    @TableField("days_overdue")
    private Integer overdueDays;

    /** 未结金额 (DB: amount) */
    @TableField("amount")
    private BigDecimal unsettledAmount;

    /** 预警级别（与 DB CHECK 对齐：INFO/WARNING/CRITICAL） */
    private String alertLevel;

    /** ACTIVE / RESOLVED / DISMISSED */
    private String status;

    /** 处理人 */
    private Long processedBy;

    /** 处理时间 */
    private LocalDateTime processedAt;
}
