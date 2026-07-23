package com.huicai.agency.tenant.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnterpriseSwitchVO {
    private Long enterpriseId;
    private String enterpriseName;
    private Boolean seedDataDone;
}
