package com.huicai.agency.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会计-客户分配关系实体 — t_agency_user_enterprise
 */
@Data
@TableName("t_agency_user_enterprise")
public class AgencyUserEnterpriseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long agencyUserId;
    private Long enterpriseId;
    private Long assignedBy;
    private LocalDateTime assignedAt;
    private Long unassignedBy;
    private LocalDateTime unassignedAt;
    private Integer deleted;
}
