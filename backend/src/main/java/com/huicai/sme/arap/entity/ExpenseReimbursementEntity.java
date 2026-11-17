package com.huicai.sme.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 费用报销单 - P11-2
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_expense_reimbursement")
public class ExpenseReimbursementEntity extends BaseEntity {

    /** 报销单号 REIMB-YYYYMM-XXXX */
    private String reimbNo;

    /** 申请人ID — 映射 DB applicant_id 列 */
    @TableField("applicant_id")
    private Long applicantId;

    /**
     * 员工ID — 保持非持久化（SPEC-P153 定性）：入参别名，L162 即
     * {@code setApplicantId(getEmployeeId())} 转存真实列 applicant_id，
     * VO 回填亦从 applicantId 取 —— 建列反而制造双真值源。
     */
    @TableField(exist = false)
    private Long employeeId;
    private Long deptId;

    /** TRAVEL/OFFICE/ENTERTAIN/TRANSPORT/COMMUNICATION/OTHER */
    @TableField("reimb_type")
    private String expenseType;

    @TableField("total_amount")
    private BigDecimal amount;
    private String summary;

    /** DRAFT/SUBMITTED/APPROVED/REJECTED/VOUCHERED */
    @StatusChangeable(entity = "EXPENSE_REIMBURSEMENT", fieldName = "status")
    private String status;

    private Long docId;
    private Long voucherId;
    /** 来源流水ID（流水自动报账的幂等键，findByBankStmtId 防重查询依赖）— V194 真实列 */
    @TableField("bank_stmt_id")
    private Long bankStmtId;
    /** 附件ID列表 — V194 真实列 */
    @TableField("attachment_ids")
    private String attachmentIds;

    /** 提交时间 — V194 真实列（页端列表直显，此前恒空） */
    @TableField("submitted_at")
    private LocalDateTime submittedAt;
    /** 审批时间 — V194 真实列 */
    @TableField("approved_at")
    private LocalDateTime approvedAt;
    private Long createdBy;
    /** 审批人姓名（SPEC-P119：非用户 id）— V194 真实列 */
    @TableField("approved_by")
    private String approvedBy;
    /** 驳回原因 — V194 真实列（ExpenseEdit 红字提示，此前恒空） */
    @TableField("reject_reason")
    private String rejectReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
