package com.huicai.module.finance.dto;

import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.entity.BusinessDocEntryEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class BusinessDocVO {
    private Long id;
    private String docNo;
    private String docType;
    private LocalDate docDate;
    private String period;
    private BigDecimal amount;
    private String status;
    private Long supplierId;
    private String supplierName;
    private Long customerId;
    private String customerName;
    private Long applicantId;
    private Long deptId;
    private String summary;
    private String enrichedSummary;
    private String source;
    private String attachmentIds;
    private Long voucherId;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private Long submittedBy;
    private String submittedByName;
    private LocalDateTime submittedAt;
    private Long approvedBy;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private List<EntryVO> entries;

    @Data
    public static class EntryVO {
        private Long id;
        private String expenseType;
        private Long subjectId;
        private String subjectCode;
        private String subjectName;
        private BigDecimal amount;
        private String invoiceNo;
        private String summary;
        private Integer sortOrder;
    }

    public static BusinessDocVO fromEntity(BusinessDocEntity e) {
        BusinessDocVO vo = new BusinessDocVO();
        vo.setId(e.getId());
        vo.setDocNo(e.getDocNo());
        vo.setDocType(e.getDocType());
        vo.setDocDate(e.getDocDate());
        vo.setPeriod(e.getPeriod());
        vo.setAmount(e.getAmount());
        vo.setStatus(e.getStatus());
        vo.setSupplierId(e.getSupplierId());
        vo.setCustomerId(e.getCustomerId());
        vo.setApplicantId(e.getApplicantId());
        vo.setDeptId(e.getDeptId());
        vo.setSummary(e.getSummary());
        vo.setSource(e.getSource());
        vo.setAttachmentIds(e.getAttachmentIds());
        vo.setVoucherId(e.getVoucherId());
        vo.setCreatedBy(e.getCreatedBy());
        vo.setCreatedAt(e.getCreatedAt());
        vo.setSubmittedBy(e.getSubmittedBy());
        vo.setSubmittedAt(e.getSubmittedAt());
        vo.setApprovedBy(e.getApprovedBy());
        vo.setApprovedAt(e.getApprovedAt());
        return vo;
    }

    public static EntryVO fromEntryEntity(BusinessDocEntryEntity e) {
        EntryVO vo = new EntryVO();
        vo.setId(e.getId());
        vo.setExpenseType(e.getExpenseType());
        vo.setSubjectId(e.getSubjectId());
        vo.setAmount(e.getAmount());
        vo.setInvoiceNo(e.getInvoiceNo());
        vo.setSummary(e.getSummary());
        vo.setSortOrder(e.getSortOrder());
        return vo;
    }
}
