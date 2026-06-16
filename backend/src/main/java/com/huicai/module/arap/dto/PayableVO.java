package com.huicai.module.arap.dto;

import com.huicai.module.arap.entity.PayableEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PayableVO {
    private Long id;
    private Long vendorId;
    private String vendorName;
    private Long docId;
    private Long voucherId;
    private String period;
    private LocalDate txDate;
    private BigDecimal amount;
    private BigDecimal settledAmount;
    private BigDecimal unsettledAmount;
    private LocalDate dueDate;
    private String summary;
    private String enrichedSummary;
    private String createdByName;
    private LocalDateTime createdAt;

    public static PayableVO fromEntity(PayableEntity e) {
        PayableVO vo = new PayableVO();
        vo.setId(e.getId());
        vo.setVendorId(e.getVendorId());
        vo.setDocId(e.getDocId());
        vo.setVoucherId(e.getVoucherId());
        vo.setPeriod(e.getPeriod());
        vo.setTxDate(e.getTxDate());
        vo.setAmount(e.getAmount());
        vo.setSettledAmount(e.getSettledAmount());
        vo.setUnsettledAmount(e.getUnsettledAmount());
        vo.setDueDate(e.getDueDate());
        vo.setSummary(e.getSummary());
        vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }
}
