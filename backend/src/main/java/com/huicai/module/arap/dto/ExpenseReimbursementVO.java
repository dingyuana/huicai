package com.huicai.module.arap.dto;

import com.huicai.module.arap.entity.ExpenseReimbursementEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ExpenseReimbursementVO {
    private Long id;
    private String reimbNo;
    private Long employeeId;
    private String employeeName;
    private Long deptId;
    private String deptName;
    private String expenseType;
    private BigDecimal amount;
    private String summary;
    private String enrichedSummary;
    private String status;
    private Long docId;
    private Long voucherId;
    private Long bankStmtId;
    private String attachmentIds;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private Long createdBy;
    private String createdByName;
    private String approvedBy;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ExpenseReimbursementVO fromEntity(ExpenseReimbursementEntity e) {
        ExpenseReimbursementVO vo = new ExpenseReimbursementVO();
        vo.setId(e.getId());
        vo.setReimbNo(e.getReimbNo());
        vo.setEmployeeId(e.getEmployeeId());
        vo.setDeptId(e.getDeptId());
        vo.setExpenseType(e.getExpenseType());
        vo.setAmount(e.getAmount());
        vo.setSummary(e.getSummary());
        vo.setStatus(e.getStatus());
        vo.setDocId(e.getDocId());
        vo.setVoucherId(e.getVoucherId());
        vo.setBankStmtId(e.getBankStmtId());
        vo.setAttachmentIds(e.getAttachmentIds());
        vo.setSubmittedAt(e.getSubmittedAt());
        vo.setApprovedAt(e.getApprovedAt());
        vo.setCreatedBy(e.getCreatedBy());
        vo.setApprovedBy(e.getApprovedBy());
        vo.setRejectReason(e.getRejectReason());
        vo.setCreatedAt(e.getCreatedAt());
        vo.setUpdatedAt(e.getUpdatedAt());
        return vo;
    }
}
