package com.huicai.module.arap.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ReconciliationToleranceDTO {

    private Long partyId;

    private String partyType;

    @DecimalMin("0.00")
    @DecimalMax("1000.00")
    private BigDecimal toleranceAmount;

    @DecimalMin("0.00")
    @DecimalMax("100.00")
    private BigDecimal toleranceRate;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;
}
