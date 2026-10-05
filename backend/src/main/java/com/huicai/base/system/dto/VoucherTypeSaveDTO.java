package com.huicai.base.system.dto;

import com.huicai.base.system.entity.VoucherTypeEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 凭证类型新增/修改入参（REQ-2026-129 / P102 DTO 隔离 批次④）。
 *
 * <p>剔除 {@code id} 与审计字段；{@code code}/{@code name} NOT NULL ⇒ 必填。
 */
@Data
public class VoucherTypeSaveDTO {

    @NotBlank(message = "凭证类型编码不能为空")
    @Size(max = 64, message = "凭证类型编码长度不能超过 64")
    private String code;

    @NotBlank(message = "凭证类型名称不能为空")
    @Size(max = 64, message = "凭证类型名称不能超过 64")
    private String name;

    private Integer sortOrder;

    @Size(max = 64, message = "编号规则长度不能超过 64")
    private String numberingRule;

    private Boolean isActive;

    @Size(max = 255, message = "备注过长")
    private String remark;

    public VoucherTypeEntity toEntity() {
        VoucherTypeEntity e = new VoucherTypeEntity();
        e.setCode(code);
        e.setName(name);
        e.setSortOrder(sortOrder);
        e.setNumberingRule(numberingRule);
        e.setIsActive(isActive);
        e.setRemark(remark);
        return e;
    }
}
