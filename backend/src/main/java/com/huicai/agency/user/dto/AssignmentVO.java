package com.huicai.agency.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AssignmentVO {
    private Long id;
    private Long agencyUserId;
    private Long enterpriseId;
    private String enterpriseName;
    private String taxId;
    private Long assignedBy;
    private LocalDateTime assignedAt;
}
