package com.huicai.base.voucher.dto;

import com.huicai.base.voucher.entity.VoucherTemplateEntity;
import lombok.Data;

/**
 * 凭证模板修改入参（REQ-2026-129 / P102 DTO 隔离 批次④）。
 *
 * <p>本 DTO 只保留 {@code VoucherTemplateEntity} 上<b>真正映射到列</b>的三个业务字段。
 * 该实体另有一批 {@code @TableField(exist = false)} 字段
 * （{@code description}/{@code classification}/{@code source}/{@code direction}/
 * {@code matchPriority}/{@code numberPrefix}/{@code createdBy}/{@code updatedBy}）
 * —— 它们<b>完全不参与 SQL</b>，收进来只会给调用方「改了会生效」的错觉
 * （AGENTS §4.2 第 10 条「幽灵字段」）。其中 {@code classification} 虽被
 * {@code VoucherTemplateServiceImpl#update} 读取，但读到的<b>恒为 null</b>。
 *
 * <p><b>待修（不在本批范围）</b>：{@code t_voucher_template.template_code} 与
 * {@code entries} 是 NOT NULL 且 Entity 里<b>没有对应字段</b>（§4.2 第 8/15 条同源形态），
 * 模板的新增路径依赖它们，本批不动，仅记录。
 */
@Data
public class VoucherTemplateUpdateDTO {

    private String name;

    private String businessType;

    private Boolean isActive;

    public VoucherTemplateEntity toEntity() {
        VoucherTemplateEntity e = new VoucherTemplateEntity();
        e.setName(name);
        e.setBusinessType(businessType);
        e.setIsActive(isActive);
        return e;
    }
}
