package com.huicai.module.arap.dto;

import com.huicai.module.arap.entity.ReceivableEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ReceivableVO {
    private Long id;
    private Long customerId;
    private String customerName;
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

    public static ReceivableVO fromEntity(ReceivableEntity e) {
        ReceivableVO vo = new ReceivableVO();
        vo.setId(e.getId());
        vo.setCustomerId(e.getCustomerId());
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
