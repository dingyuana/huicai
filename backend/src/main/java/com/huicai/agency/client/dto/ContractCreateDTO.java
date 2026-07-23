package com.huicai.agency.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ContractCreateDTO {
    @NotNull private Long enterpriseId;
    @NotNull private Long agencyId;
    @NotBlank private String contractNo;
    @NotNull private LocalDate startDate;
    @NotNull private LocalDate endDate;
    private String contractType = "ACCOUNTING";
    private BigDecimal amount = BigDecimal.ZERO;
}
