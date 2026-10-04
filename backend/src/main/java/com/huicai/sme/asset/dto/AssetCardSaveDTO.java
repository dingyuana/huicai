package com.huicai.sme.asset.dto;

import com.huicai.sme.asset.entity.AssetCardEntity;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 资产卡片新增/修改入参（REQ-2026-129 / P102 DTO 隔离 批次③）。
 *
 * <p><b>为何必须有 DTO</b>：{@code chk_asset_status} 允许
 * DRAFT/IN_USE/IDLE/DISPOSED/SCRAPPED，而原端点直收 {@code AssetCardEntity}，
 * 可直接 POST {@code status=DISPOSED} 造出「已处置资产」、
 * POST {@code status=SCRAPPED} 造出「已报废资产」—— 绕过资产生命周期
 * （铁律 #1 人审 + #4 状态机）。此前 Service 侧已用
 * 「无条件 {@code setStatus(IN_USE)}」把这条路堵上，但那是**兜底**，
 * DTO 化才是正解（与 REQ-2026-121 处 {@code BudgetServiceImpl} 同因同治）。
 *
 * <p>同时剔除全部金额/折旧派生字段 {@code accumulatedDepreciation}、
 * {@code netValue}、{@code lastDepreciationPeriod}（计提折旧产生，
 * 且 {@code AssetCardController} 有独立计提端点）、{@code voucherId}
 * （制证产生）、以及 {@code id/createdBy/updatedBy/createdAt/updatedAt/deleted}。
 */
@Data
public class AssetCardSaveDTO {

    @NotBlank(message = "资产编码不能为空")
    @Size(max = 64, message = "资产编码长度不能超过 64")
    private String assetCode;

    @NotBlank(message = "资产名称不能为空")
    @Size(max = 128, message = "资产名称长度不能超过 128")
    private String assetName;

    @NotNull(message = "资产分类不能为空")
    private Long categoryId;

    @Size(max = 128, message = "规格过长")
    private String spec;

    private Long deptId;

    private Long custodianId;

    @NotNull(message = "取得日期不能为空")
    private LocalDate acquisitionDate;

    @NotNull(message = "原值不能为空")
    private BigDecimal originalValue;

    /** 预计使用年限：DB NOT NULL 且无默认值 ⇒ 必填（原 DTO 漏了它，插入会挂） */
    @NotNull(message = "使用年限不能为空")
    @Min(value = 1, message = "使用年限必须大于 0")
    private Integer usefulLife;

    @Size(max = 64, message = "存放地点过长")
    private String location;

    @Size(max = 64, message = "序列号长度不能超过 64")
    private String serialNo;

    @Size(max = 500, message = "备注过长")
    private String remark;

    /**
     * 折旧方法。取值以 <b>DB 默认值 {@code STRAIGHT_LINE}</b> 与
     * {@code AssetCardServiceImpl:142} 的兜底为准（实测二者一致）；
     * 中文「直线法」是<b>历史夹具里的幻觉值</b>，真库无此取值。
     */
    @Pattern(regexp = "^$|STRAIGHT_LINE|DOUBLE_DECLINING|SUM_OF_YEARS|UNITS_OF_WORK",
            message = "折旧方法取值不合法")
    private String depreciationMethod;

    public AssetCardEntity toEntity() {
        AssetCardEntity e = new AssetCardEntity();
        e.setAssetCode(assetCode);
        e.setAssetName(assetName);
        e.setCategoryId(categoryId);
        e.setSpec(spec);
        e.setDeptId(deptId);
        e.setCustodianId(custodianId);
        e.setAcquisitionDate(acquisitionDate);
        e.setOriginalValue(originalValue);
        e.setUsefulLife(usefulLife);
        e.setLocation(location);
        e.setSerialNo(serialNo);
        e.setRemark(remark);
        e.setDepreciationMethod(depreciationMethod);
        return e;
    }
}
