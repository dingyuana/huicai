package com.huicai.agency.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgencyCreateDTO {
    @NotBlank(message = "代理公司编码不能为空")
    private String agencyCode;
    @NotBlank(message = "代理公司名称不能为空")
    private String agencyName;
    private String contactName;
    private String contactPhone;
}
