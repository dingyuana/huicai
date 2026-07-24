package com.huicai.agency.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignmentCreateDTO {
    @NotNull(message = "代理用户ID不能为空")
    private Long agencyUserId;

    @NotNull(message = "企业ID不能为空")
    private Long enterpriseId;
}
