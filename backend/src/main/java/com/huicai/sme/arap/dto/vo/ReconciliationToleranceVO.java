package com.huicai.sme.arap.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ReconciliationToleranceVO {

    private Long id;

    private Long partyId;

    private String partyType;

    private BigDecimal toleranceAmount;

    private BigDecimal toleranceRate;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
