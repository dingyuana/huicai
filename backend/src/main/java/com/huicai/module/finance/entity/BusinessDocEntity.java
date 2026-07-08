package com.huicai.module.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import com.huicai.module.system.handler.JsonbTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 业务单据主表实体
 */
@Data
@TableName("t_business_doc")
public class BusinessDocEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 单据编号 */
    private String docNo;

    /** 单据类型 */
    private String docType;

    /** 单据日期 */
    private LocalDate docDate;

    /** 会计期间(YYYYMM) */
    private String period;

    /** 单据金额 */
    private BigDecimal amount;

    /** 状态 */
    @StatusChangeable(entity = "BUSINESS_DOC", fieldName = "status")
    private String status;

    /** 供应商ID */
    private Long supplierId;

    /** 客户ID */
    private Long customerId;

    /** 申请人ID */
    private Long applicantId;

    /** 部门ID */
    private Long deptId;

    /** 摘要 */
    private String summary;

    /** 来源: MANUAL/OCR/IMPORTED */
    private String source;

    /** 发票号（从发票导入时记录） */
    private String invoiceNo;

    /** 关联销售发票ID（P1 新增） */
    private Long invoiceId;

    /** OCR 数据 (JSONB) */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String ocrData;

    /** 附件ID列表 */
    private String attachmentIds;

    /** 生成的凭证ID */
    private Long voucherId;

    /** 生成的凭证编号 */
    private String voucherNo;

    /** 被红冲单据ID */
    private Long reversedFrom;

    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    private Long updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private Long submittedBy;
    private LocalDateTime submittedAt;

    private Long approvedBy;
    private LocalDateTime approvedAt;

    @TableLogic
    private Integer deleted;

    // ==================== P34 结算字段 ====================

    /** 已核销金额 */
    private BigDecimal settledAmount;

    /** 未核销金额 */
    private BigDecimal unsettledAmount;

    /** 到期日 */
    private LocalDate dueDate;

    /** 来源银行流水ID（P38-F6: 反向追溯银行流水→单据） */
    @TableField("bank_stmt_id")
    private Long bankStatementId;

    // ==================== 乐观锁 ====================

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
