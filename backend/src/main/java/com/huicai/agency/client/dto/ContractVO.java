package com.huicai.agency.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContractVO {
    private Long id;
    private Long enterpriseId;
    private Long agencyId;
    private String contractNo;
    private LocalDate startDate;
    private LocalDate endDate;
    private String contractType;
    private BigDecimal amount;
    private String status;
    private Boolean renewalNoticeSent;
    private LocalDateTime createdAt;
}
