package com.huicai.agency.tenant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_enterprise")
public class EnterpriseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String enterpriseCode;
    private String enterpriseName;
    private String taxId;
    private String mode;
    private Long agencyId;
    private String status;
    private Boolean seedDataDone;
    private String startPeriod;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}