package com.huicai.agency.tenant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_agency")
public class AgencyEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String agencyCode;
    private String agencyName;
    private String contactName;
    private String contactPhone;
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}