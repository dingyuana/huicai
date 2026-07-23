package com.huicai.agency.tenant.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnterpriseSimpleVO {
    private Long id;
    private String enterpriseName;
    private String taxId;
    private String status;
    private Boolean seedDataDone;
}
