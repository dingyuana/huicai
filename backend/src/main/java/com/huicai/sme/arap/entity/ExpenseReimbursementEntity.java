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

    /** 员工ID — 仅为 VO/前端契约字段，DB 无 employee_id 列 */
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
    @TableField(exist = false)
    private Long bankStmtId;
    @TableField(exist = false)
    private String attachmentIds;

    @TableField(exist = false)
    private LocalDateTime submittedAt;
    @TableField(exist = false)
    private LocalDateTime approvedAt;
    private Long createdBy;
    @TableField(exist = false)
    private String approvedBy;
    @TableField(exist = false)
    private String rejectReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
