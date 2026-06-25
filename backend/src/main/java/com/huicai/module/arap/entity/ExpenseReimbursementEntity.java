package com.huicai.module.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 费用报销单 - P11-2
 */
@Data
@TableName("t_expense_reimbursement")
public class ExpenseReimbursementEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 报销单号 REIMB-YYYYMM-XXXX */
    private String reimbNo;

    private Long employeeId;
    private Long deptId;

    /** TRAVEL/OFFICE/ENTERTAIN/TRANSPORT/COMMUNICATION/OTHER */
    private String expenseType;

    private BigDecimal amount;
    private String summary;

    /** DRAFT/SUBMITTED/APPROVED/REJECTED/VOUCHERED */
    @StatusChangeable(entity = "EXPENSE_REIMBURSEMENT", fieldName = "status")
    private String status;

    private Long docId;
    private Long voucherId;
    private Long bankStmtId;
    private String attachmentIds;

    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private Long createdBy;
    private String approvedBy;
    private String rejectReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
