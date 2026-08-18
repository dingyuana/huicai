package com.huicai.base.business.dto;

import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.business.entity.BusinessDocEntryEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class BusinessDocDTO {
    private Long id;
    private String docNo;
    private String docType;
    private LocalDate docDate;
    private String period;
    private BigDecimal amount;
    private Long supplierId;
    private Long customerId;
    private Long applicantId;
    private Long deptId;
    private String summary;
    private String attachmentIds;
    private Long settlementAccountId;
    private List<EntryDTO> entries;

    @Data
    public static class EntryDTO {
        private Long id;
        private String expenseType;
        private Long subjectId;
        private BigDecimal amount;
        private String invoiceNo;
        private String assistJson;
        private String summary;
        private Integer sortOrder;
    }

    public static BusinessDocEntity toEntity(BusinessDocDTO dto) {
        BusinessDocEntity e = new BusinessDocEntity();
        e.setId(dto.getId());
        e.setDocNo(dto.getDocNo());
        e.setDocType(dto.getDocType());
        e.setDocDate(dto.getDocDate());
        e.setPeriod(dto.getPeriod());
        e.setAmount(dto.getAmount());
        e.setSupplierId(dto.getSupplierId());
        e.setCustomerId(dto.getCustomerId());
        e.setApplicantId(dto.getApplicantId());
        e.setDeptId(dto.getDeptId());
        e.setSummary(dto.getSummary());
        e.setAttachmentIds(dto.getAttachmentIds());
        e.setSettlementAccountId(dto.getSettlementAccountId());
        return e;
    }

    public static BusinessDocEntryEntity toEntryEntity(Long docId, EntryDTO dto, int sortOrder) {
        BusinessDocEntryEntity e = new BusinessDocEntryEntity();
        e.setId(dto.getId());
        e.setDocId(docId);
        e.setExpenseType(dto.getExpenseType());
        e.setSubjectId(dto.getSubjectId());
        e.setAmount(dto.getAmount());
        e.setInvoiceNo(dto.getInvoiceNo());
        e.setAssistJson(dto.getAssistJson());
        e.setSummary(dto.getSummary());
        e.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : sortOrder);
        return e;
    }
}
