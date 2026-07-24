package com.huicai.agency.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 代理公司内部用户角色实体 — t_agency_user
 */
@Data
@TableName("t_agency_user")
public class AgencyUserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long agencyId;
    private Long userId;
    private String agencyRole;
    private String status;

    @TableField(exist = false)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    @Version
    private Integer version;
}
