package com.huicai.sme.asset.dto;

import com.huicai.sme.asset.entity.AssetCategoryEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 资产分类新增/修改入参（REQ-2026-129 / P102 DTO 隔离 批次④）。
 *
 * <p>{@code depreciationMethod} 的允许集以 {@code chk_dep_method} 为准：
 * {@code STRAIGHT_LINE / DOUBLE_DECLINING / SUM_OF_YEARS}（实测查证；中文
 * 「直线法」是历史夹具里的幻觉值，真库无此取值）。
 */
@Data
public class AssetCategorySaveDTO {

    @NotBlank(message = "分类编码不能为空")
    @Size(max = 64, message = "分类编码长度不能超过 64")
    private String code;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 128, message = "分类名称长度不能超过 128")
    private String name;

    private Long parentId;

    private Integer level;

    @Pattern(regexp = "^$|STRAIGHT_LINE|DOUBLE_DECLINING|SUM_OF_YEARS",
            message = "折旧方法仅支持 STRAIGHT_LINE / DOUBLE_DECLINING / SUM_OF_YEARS")
    private String depreciationMethod;

    private Integer usefulLife;

    private BigDecimal residualRate;

    private Long assetSubjectId;

    private Long depreciationSubjectId;

    private Long expenseSubjectId;

    @Size(max = 500, message = "备注过长")
    private String remark;

    public AssetCategoryEntity toEntity() {
        AssetCategoryEntity e = new AssetCategoryEntity();
        e.setCode(code);
        e.setName(name);
        e.setParentId(parentId);
        e.setLevel(level);
        e.setDepreciationMethod(depreciationMethod);
        e.setUsefulLife(usefulLife);
        e.setResidualRate(residualRate);
        e.setAssetSubjectId(assetSubjectId);
        e.setDepreciationSubjectId(depreciationSubjectId);
        e.setExpenseSubjectId(expenseSubjectId);
        e.setRemark(remark);
        return e;
    }
}
