package com.huicai.agency.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EnterpriseCreateDTO {
    @NotBlank(message = "企业编码不能为空")
    private String enterpriseCode;
    @NotBlank(message = "企业名称不能为空")
    private String enterpriseName;
    private String taxId;
    private Long agencyId;
}
