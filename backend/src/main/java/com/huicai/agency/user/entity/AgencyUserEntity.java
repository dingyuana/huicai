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

    /* P110 Phase 3：created_by/updated_by 真实存在于 t_agency_user，
     * 旧写法 @TableField(exist = false) 会让列永久不写入。改为普通字段。 */
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    @Version
    private Integer version;
}
