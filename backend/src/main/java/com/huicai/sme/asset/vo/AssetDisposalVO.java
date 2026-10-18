package com.huicai.sme.asset.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.sme.asset.entity.AssetDisposalEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 资产处置出参 VO（P102 出参面批次 14）
 *
 * <p><b>字段集来源</b>：{@code t_asset_disposal} 的真实列（`PG \d` 实测 18 列）减去
 * 服务端内部字段 —— 逐个核对后与前端
 * {@code frontend/src/views/asset/disposal/AssetDisposalList.vue} 实际渲染的字段一致：
 * {@code disposalNo/disposalType/disposalDate/originalValue/netValue/gainLoss/status}。
 *
 * <p><b>⚠️ 前端 interface 曾声明而后端无此列</b>（AGENTS §4.5 第 38 条形态）：
 * {@code frontend/src/api/modules/asset.ts#AssetDisposal} 声明了
 * {@code assetCardId / disposalValue / netBookValue / reason} 四字段，
 * 而 `t_asset_disposal` **一个都没有**（真实列是 {@code asset_id / original_value /
 * net_value}，且无 {@code remark}）。逐个 grep 页面引用：四者**引用行数均为 0**
 * ⇒ 属**契约陈旧**而非「功能未实现」，已从前端 interface 删除。
 * 反之页面真正渲染的 {@code disposalNo/originalValue/netValue} 却不在 interface 里
 * ⇒ 一并补上。判断依据是「grep 页面有没有真的读它」而非字段名相似。
 *
 * <p><b>刻意不外露</b>：{@code deleted}（逻辑删除位）、{@code version}（乐观锁）、
 * {@code enterpriseId}（租户内部维度）、{@code updatedBy}（⚠️ 该字段在 Entity 里
 * 标了 {@code @TableField(exist = false)}，是**幽灵字段**——DB 无此列、赋值不参与 SQL、
 * 读回恒 null，AGENTS §4.2 第 10 条）。
 *
 * @see AGENTS.md §0「出参面 Entity 直出」待办
 */
@Data
public class AssetDisposalVO {

    private Long id;
    private String disposalNo;
    private Long assetId;
    private String disposalType;
    private LocalDate disposalDate;
    private String period;
    private BigDecimal originalValue;
    private BigDecimal accumulatedDepreciation;
    private BigDecimal netValue;
    private BigDecimal disposalIncome;
    private BigDecimal disposalExpense;
    private BigDecimal gainLoss;
    private String status;
    private Long voucherId;

    public static AssetDisposalVO from(AssetDisposalEntity e) {
        if (e == null) {
            return null;
        }
        AssetDisposalVO vo = new AssetDisposalVO();
        vo.setId(e.getId());
        vo.setDisposalNo(e.getDisposalNo());
        vo.setAssetId(e.getAssetId());
        vo.setDisposalType(e.getDisposalType());
        vo.setDisposalDate(e.getDisposalDate());
        vo.setPeriod(e.getPeriod());
        vo.setOriginalValue(e.getOriginalValue());
        vo.setAccumulatedDepreciation(e.getAccumulatedDepreciation());
        vo.setNetValue(e.getNetValue());
        vo.setDisposalIncome(e.getDisposalIncome());
        vo.setDisposalExpense(e.getDisposalExpense());
        vo.setGainLoss(e.getGainLoss());
        vo.setStatus(e.getStatus());
        vo.setVoucherId(e.getVoucherId());
        return vo;
    }

    public static List<AssetDisposalVO> from(List<AssetDisposalEntity> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(AssetDisposalVO::from).collect(java.util.stream.Collectors.toList());
    }

    /** Entity 分页 → VO 分页；只换 records 元素类型，分页元信息原样透传 */
    public static IPage<AssetDisposalVO> from(IPage<AssetDisposalEntity> page) {
        if (page == null) {
            return null;
        }
        Page<AssetDisposalVO> out = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(from(page.getRecords()));
        return out;
    }
}