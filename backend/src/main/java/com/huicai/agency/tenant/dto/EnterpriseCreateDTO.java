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

    /** 联系人（SPEC-P129：可选） */
    private String contactName;
    /** 电话（SPEC-P129：可选；固话含区号，禁止用 Long） */
    private String contactPhone;
    /** 邮箱（SPEC-P129：可选） */
    private String contactEmail;
    /** 地址（SPEC-P129：可选） */
    private String address;
}
