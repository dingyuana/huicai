package com.huicai.module.finance.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量状态操作 DTO
 */
@Data
public class VoucherStatusDTO {

    /** 凭证ID列表 */
    @NotEmpty(message = "凭证ID列表不能为空")
    private List<Long> ids;
}
