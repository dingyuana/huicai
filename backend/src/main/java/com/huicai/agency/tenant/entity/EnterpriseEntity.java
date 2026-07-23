package com.huicai.agency.tenant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_enterprise")
public class EnterpriseEntity extends BaseEntity {
    private String enterpriseCode;
    private String enterpriseName;
    private String taxId;
    private String mode;
    private Long agencyId;
    private String status;
    private Boolean seedDataDone;
}
