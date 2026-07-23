package com.huicai.agency.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgencyVO {
    private Long id;
    private String agencyCode;
    private String agencyName;
    private String contactName;
    private String contactPhone;
    private String status;
    private LocalDateTime createdAt;
}
