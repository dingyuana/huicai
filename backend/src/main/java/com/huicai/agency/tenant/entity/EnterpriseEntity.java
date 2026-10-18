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

    // ── SPEC-P129：联系信息（V184 补列，全部可空）────────────────────────
    /** 联系人 */
    private String contactName;
    /** 电话（手机或含区号固话，故用 String 不用 Long） */
    private String contactPhone;
    /** 邮箱 */
    private String contactEmail;
    /** 地址 */
    private String address;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}