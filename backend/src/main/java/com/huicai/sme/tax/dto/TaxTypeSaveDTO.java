package com.huicai.sme.tax.dto;

import com.huicai.sme.tax.entity.TaxTypeEntity;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 税种新增/修改入参（REQ-2026-129 / P102 DTO 隔离 批次③ · 补漏）。
 *
 * <p><b>补漏说明</b>：本类是被<b>结构性守卫</b>（而非门禁脚本）发现的 ——
 * {@code TaxController#createTaxType/updateTaxType} 一直直收
 * {@code TaxTypeEntity} 违反铁律 #13，但
 * {@code scripts/check_entity_status_massassignment.py} 只按
 * 「status 等状态字段是否可被客户端指定」判定，而 {@code TaxTypeEntity}
 * <b>没有 status 字段</b> ⇒ 门禁判它无违规。
 * ⇒ <b>字段级门禁必然漏掉「无状态字段的 Entity 直收」</b>，必须配一个
 * 「参数类型即守卫」的反射断言（见 {@code TenantDtoIsolationStructureTest}）。
 *
 * <p>{@code isActive} 是「该税种是否启用」的<b>业务开关</b>（非人工流程状态），
 * 故保留；剔除 {@code createdAt/updatedAt/deleted}。
 */
@Data
public class TaxTypeSaveDTO {

    @NotBlank(message = "税种编码不能为空")
    @Size(max = 32, message = "税种编码长度不能超过 32")
    private String code;

    @NotBlank(message = "税种名称不能为空")
    @Size(max = 64, message = "税种名称长度不能超过 64")
    private String name;

    @Size(max = 64, message = "税种类别长度不能超过 64")
    private String taxCategory;

    @NotNull(message = "税率不能为空")
    @DecimalMin(value = "0.0", message = "税率不能为负")
    private BigDecimal rate;

    private Boolean isActive;

    @Size(max = 500, message = "备注过长")
    private String remark;

    public TaxTypeEntity toEntity() {
        TaxTypeEntity e = new TaxTypeEntity();
        e.setCode(code);
        e.setName(name);
        e.setTaxCategory(taxCategory);
        e.setRate(rate);
        e.setIsActive(isActive);
        e.setRemark(remark);
        return e;
    }
}
