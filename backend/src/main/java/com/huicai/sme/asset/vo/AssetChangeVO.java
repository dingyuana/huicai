package com.huicai.sme.asset.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * SPEC-P144（D-4）：资产变动记录出参。
 *
 * <p>{@code beforeValue/afterValue} 是调整维度的单值快照（列宽 500），
 * 形态如 {@code originalValue=50000.00} / {@code deptId=3} —— 前缀标明
 * 调整的是哪个维度，供前端直接展示「原值 50000 → 60000」。</p>
 *
 * <p>{@code voucherNo} 为查询时回填的展示字段（凭证号冗余，
 * 源头是 t_voucher.voucher_no），实体上不存在。</p>
 */
@Data
public class AssetChangeVO {

    private Long id;

    private Long assetId;

    private String assetCode;

    private String assetName;

    private String changeType;

    private String beforeValue;

    private String afterValue;

    private LocalDate changeDate;

    private Long voucherId;

    /** 查询回填：变动生成的凭证号（无凭证的调整类型为 null）。 */
    private String voucherNo;

    private String remark;
}
