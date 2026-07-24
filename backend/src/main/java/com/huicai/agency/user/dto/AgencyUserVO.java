package com.huicai.agency.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgencyUserVO {
    private Long id;
    private Long agencyId;
    private Long userId;
    private String username;
    private String realName;
    private String agencyRole;
    private String status;
    private int enterpriseCount;
    private LocalDateTime createdAt;
}
