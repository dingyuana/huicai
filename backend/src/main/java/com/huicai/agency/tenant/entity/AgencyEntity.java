package com.huicai.agency.tenant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_agency")
public class AgencyEntity extends BaseEntity {
    private String agencyCode;
    private String agencyName;
    private String contactName;
    private String contactPhone;
    private String status;
}
