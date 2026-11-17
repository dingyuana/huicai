package com.huicai.base.business.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_bank_statement")
public class BankStatementEntity extends BaseEntity {

    private Long accountId;
    private LocalDate txDate;
    private String txType;
    private String counterAccount;
    private BigDecimal amount;
    private String summary;
    @TableField("purpose")
    private String purpose;
    @TableField("transaction_remark")
    private String transactionRemark;
    private String externalNo;
    private String rawData;
    private Long matchedJournalId;
    private String matchStatus;

    // P1 业务分类字段 (V17)
    @TableField(exist = false)
    private String direction;             // 业务方向 in/out — 由 txType 严格派生，刻意不建列
    /** 导入批号 — V194 真实列（导入批次追溯） */
    @TableField("batch_id")
    private String batchId;
    /** 命中的分类规则ID — V194 真实列（分类结果溯源到规则） */
    @TableField("rule_id")
    private Long ruleId;
    // 🔴 SPEC-P153 遗留登记：AI 分类溯源 3 字段按「AI 项目除外」裁定暂缓，
    // 仍为幽灵字段（写了不落库）；AI 侧整改时与列/读回消费方同批转正。
    @TableField(exist = false)
    private Integer aiConfidence;         // AI 置信度 0-100
    @TableField(exist = false)
    private String aiSuggestedAction;     // AI 建议分类
    @TableField(exist = false)
    private String aiBusinessScene;       // AI 业务场景
    // DB 持久化: 三级科目（规则命中时写入）
    @TableField(value = "subject_level1")
    private String subjectLevel1;         // 一级科目
    @TableField(value = "subject_level2")
    private String subjectLevel2;         // 二级科目
    @TableField(value = "subject_level3")
    private String subjectLevel3;         // 三级科目
    // DB 列 category → Java 字段 classification（兼容旧代码引用）
    @TableField(value = "category")
    private String classification;        // 业务分类（DB 列名 category）
    @StatusChangeable(entity = "BANK_STATEMENT", fieldName = "reviewStatus")
    private String reviewStatus;          // 出纳确认状态
    /** 确认/复核人 — V194 真实列（自动制证以复核人身份发起，此前恒 null） */
    @TableField("reviewed_by")
    private Long reviewedBy;
    /** 确认/复核时间 — V194 真实列 */
    @TableField("reviewed_at")
    private LocalDateTime reviewedAt;

    // V22: 自动生成单据与凭证 — V194 全部转真实列（流水→单→证 溯源闭环）
    /** 生成的业务单据ID */
    @TableField("generated_doc_id")
    private Long generatedDocId;
    /** 生成的会计凭证ID */
    @TableField("generated_voucher_id")
    private Long generatedVoucherId;
    /** 生成时间 */
    @TableField("generated_at")
    private LocalDateTime generatedAt;

    /** 生成的凭证号（流水行直接展示）— V194 真实列 */
    @TableField("generated_voucher_no")
    private String generatedVoucherNo;

    /* P110 Phase 3: t_bank_statement.generated_doc_no 真实存在，
     * 旧 exist=false 注解导致该字段从未持久化。已清除标注，
     * AutoGenerationService 三条路径同步写入该列。 */
    private String generatedDocNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime importedAt;

    // SPEC-P153：删除对 BaseEntity created_by/created_at/updated_by/updated_at 的
    // exist=false 覆盖。四列真库全部存在（created_by V178 补、updated_by 更早、
    // created_at/updated_at V194 补）；P117 修 BaseEntity 时漏了本表，
    // 导致 setReviewedBy/setCreatedBy 全不落库。

    @TableLogic
    private Integer deleted;

    /** 乐观锁版本号 */
    @Version
    @TableField("version")
    private Integer version;
}
