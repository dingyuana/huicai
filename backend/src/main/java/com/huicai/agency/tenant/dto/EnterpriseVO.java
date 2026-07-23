package com.huicai.agency.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnterpriseVO {
    private Long id;
    private String enterpriseCode;
    private String enterpriseName;
    private String taxId;
    private String mode;
    private Long agencyId;
    private String status;
    private Boolean seedDataDone;
    private LocalDateTime createdAt;
}
