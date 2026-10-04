package com.huicai.sme.asset.dto;

import com.huicai.sme.asset.entity.AssetDisposalEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 资产处置单新增入参（REQ-2026-129 / P102 DTO 隔离 批次③）。
 *
 * <p>剔除 {@code status}（Service 固定置 DRAFT，审批走
 * {@code POST /{id}/approve}；{@code chk_disposal_status} 允许
 * DRAFT/APPROVED/VOUCHERED，直收 Entity 可直接造出「已审批/已制证」单，
 * 绕过人审与制证两步）与 {@code voucherId}（制证产生）。
 *
 * <p>⚠️ <b>注意</b>：{@code originalValue}/{@code accumulatedDepreciation}/
 * {@code netValue}/{@code gainLoss} 在 DB 侧 NOT NULL 且无默认值，但
 * {@code AssetDisposalServiceImpl#create} 会用卡片上的值<b>覆盖</b>
 * {@code originalValue/accumulatedDepreciation/netValue} 并自行计算
 * {@code gainLoss} ⇒ 这四个字段属<b>服务端派生</b>，客户端提交必然被丢弃。
 * 因此本 DTO 不接收它们，若 Service 的覆盖逻辑变更需同步调整此处。
 */
@Data
public class AssetDisposalCreateDTO {

    /** 处置单号：DB 侧 NOT NULL 且无默认值，Service 亦不生成（实测）⇒ 客户端必填 */
    @NotBlank(message = "处置单号不能为空")
    @Size(max = 64, message = "处置单号长度不能超过 64")
    private String disposalNo;

    @NotNull(message = "资产 ID 不能为空")
    private Long assetId;

    /** 处置方式：chk_disposal_type 允许 SCRAP / SALE / DONATE / INV_LOSS */
    @NotBlank(message = "处置方式不能为空")
    @Pattern(regexp = "SCRAP|SALE|DONATE|INV_LOSS",
            message = "处置方式仅支持 SCRAP / SALE / DONATE / INV_LOSS")
    private String disposalType;

    @NotNull(message = "处置日期不能为空")
    private LocalDate disposalDate;

    @Size(max = 16, message = "期间长度不能超过 16")
    private String period;

    /** 处置收入 */
    private BigDecimal disposalIncome;

    /** 处置费用 */
    private BigDecimal disposalExpense;

    public AssetDisposalEntity toEntity() {
        AssetDisposalEntity e = new AssetDisposalEntity();
        e.setDisposalNo(disposalNo);
        e.setAssetId(assetId);
        e.setDisposalType(disposalType);
        e.setDisposalDate(disposalDate);
        e.setPeriod(period);
        e.setDisposalIncome(disposalIncome);
        e.setDisposalExpense(disposalExpense);
        return e;
    }
}
