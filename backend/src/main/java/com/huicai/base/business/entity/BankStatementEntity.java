package com.huicai.base.business.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_bank_statement")
public class BankStatementEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long accountId;
    private LocalDate txDate;
    private String txType;
    private String counterAccount;
    private BigDecimal amount;
    private String summary;
    @TableField(exist = false)
    private String purpose;
    @TableField(exist = false)
    private String transactionRemark;
    private String externalNo;
    private String rawData;
    private Long matchedJournalId;
    private String matchStatus;

    // P1 业务分类字段 (V17)
    // P1 业务分类字段 (V17) — DB 无对应列，仅内存缓存
    @TableField(exist = false)
    private String direction;             // 业务方向 in/out
    @TableField(exist = false)
    private String batchId;               // 导入批号
    @TableField(exist = false)
    private Long ruleId;                  // 命中规则 ID
    @TableField(exist = false)
    private Integer aiConfidence;         // AI 置信度 0-100
    @TableField(exist = false)
    private String aiSuggestedAction;     // AI 建议分类
    @TableField(exist = false)
    private String aiBusinessScene;       // AI 业务场景
    // DB 列 category → Java 字段 classification（兼容旧代码引用）
    @TableField(value = "category")
    private String classification;        // 业务分类（DB 列名 category）
    @StatusChangeable(entity = "BANK_STATEMENT", fieldName = "reviewStatus")
    private String reviewStatus;          // 出纳确认状态
    @TableField(exist = false)
    private Long reviewedBy;              // 审核人
    @TableField(exist = false)
    private LocalDateTime reviewedAt;     // 审核时间

    // V22: 自动生成单据与凭证
    @TableField(exist = false)
    private Long generatedDocId;          // 生成的业务单据 ID
    @TableField(exist = false)
    private Long generatedVoucherId;      // 生成的会计凭证 ID
    @TableField(exist = false)
    private LocalDateTime generatedAt;    // 生成时间

    // 非持久化: 用于前端展示凭证号/单据号
    @TableField(exist = false)
    private String generatedVoucherNo;

    @TableField(exist = false)
    private String generatedDocNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime importedAt;

    @TableLogic
    private Integer deleted;

    /** 乐观锁版本号 — DB 无此列 */
    @TableField(exist = false)
    @Version
    private Integer version;
}
