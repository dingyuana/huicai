package com.huicai.module.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 凭证主表实体
 */
@Data
@TableName("t_voucher")
public class VoucherEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 凭证号(格式: 类型+年份+月份+流水号) */
    private String voucherNo;

    /** 会计期间(YYYYMM) */
    private String period;

    /** 凭证类型ID */
    private Long voucherTypeId;

    /** 状态: DRAFT-草稿, SUBMITTED-已提交, AUDITED-已审核, POSTED-已记账 */
    private String status;

    /** 借方总金额 */
    private BigDecimal totalDebit;

    /** 贷方总金额 */
    private BigDecimal totalCredit;

    /** 摘要 */
    private String summary;

    /** 来源: MANUAL-手工录入, TEMPLATE-模板生成, GENERATED-单据生成, REVERSAL-红冲 */
    private String source;

    /** 附件ID列表(逗号分隔) */
    private String attachmentIds;

    /** 制单人 */
    private Long createdBy;

    /** 制单时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新人 */
    private Long updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 提交人 */
    private Long submittedBy;

    /** 提交时间 */
    private LocalDateTime submittedAt;

    /** 审核人 */
    private Long auditedBy;

    /** 审核时间 */
    private LocalDateTime auditedAt;

    /** 记账人 */
    private Long postedBy;

    /** 记账时间 */
    private LocalDateTime postedAt;

    /** 被红冲凭证ID */
    private Long reversedFrom;

    /** 逻辑删除(0-未删,1-已删) */
    @TableLogic
    private Integer deleted;
}
