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
    private String purpose;
    private String transactionRemark;
    private String externalNo;
    private String rawData;
    private Long matchedJournalId;
    private String matchStatus;

    // P1 业务分类字段 (V17)
    private String direction;             // 业务方向 in/out
    private String batchId;               // 导入批号
    private String classification;        // 业务分类
    private Long ruleId;                  // 命中规则 ID
    private Integer aiConfidence;         // AI 置信度 0-100
    private String aiSuggestedAction;     // AI 建议分类
    private String aiBusinessScene;       // AI 业务场景
    @StatusChangeable(entity = "BANK_STATEMENT", fieldName = "reviewStatus")
    private String reviewStatus;          // 出纳确认状态
    private Long reviewedBy;              // 审核人
    private LocalDateTime reviewedAt;     // 审核时间

    // V22: 自动生成单据与凭证
    private Long generatedDocId;          // 生成的业务单据 ID
    private Long generatedVoucherId;      // 生成的会计凭证 ID
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

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
