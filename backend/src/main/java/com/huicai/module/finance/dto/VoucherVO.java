package com.huicai.module.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 凭证视图对象
 */
@Data
public class VoucherVO {

    private Long id;
    private String voucherNo;
    private String period;
    private Long voucherTypeId;
    private String voucherTypeName;
    private String voucherTypeCode;
    private String status;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private String summary;
    private String source;
    private String attachmentIds;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
    private Long submittedBy;
    private String submittedByName;
    private LocalDateTime submittedAt;
    private Long auditedBy;
    private String auditedByName;
    private LocalDateTime auditedAt;
    private Long postedBy;
    private String postedByName;
    private LocalDateTime postedAt;
    private Long reversedFrom;
    private List<EntryVO> entries;

    /**
     * 分录视图对象
     */
    @Data
    public static class EntryVO {
        private Long id;
        private Long subjectId;
        private String subjectCode;
        private String subjectName;
        private BigDecimal debit;
        private BigDecimal credit;
        private String summary;
        private String assistJson;
        private Integer sortOrder;
    }
}
