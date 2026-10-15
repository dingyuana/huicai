package com.huicai.agency.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnterpriseVO {
    private Long id;
    private String enterpriseCode;
    private String enterpriseName;
    private String taxId;
    private String mode;
    private Long agencyId;
    private String status;
    private Boolean seedDataDone;
    private LocalDateTime createdAt;

    /** 联系人 */
    private String contactName;
    /**
     * 电话 —— PII，非管理员由服务端脱敏（SPEC-P129 D-1 裁定）。
     */
    private String contactPhone;
    /** 邮箱 —— PII，非管理员由服务端脱敏（SPEC-P129 D-1 裁定）。 */
    private String contactEmail;
    /** 地址 */
    private String address;
}
