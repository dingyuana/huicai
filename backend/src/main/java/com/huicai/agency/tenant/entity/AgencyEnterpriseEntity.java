package com.huicai.agency.tenant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_agency_enterprise")
public class AgencyEnterpriseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long agencyId;
    private Long enterpriseId;
    private String status;
    private LocalDateTime createdAt;
}
