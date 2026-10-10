package com.huicai.sme.asset.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.sme.asset.entity.AssetInventoryEntity;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 资产盘点出参 VO（P102 出参面批次 14）
 *
 * <p><b>字段集来源</b>：{@code t_asset_inventory} 的真实列（{@code PG \d} 实测 14 列）
 * 减去服务端内部字段。
 *
 * <p><b>⚠️ 前端 interface 与后端严重不符，且页面是空壳</b>：
 * 前端 {@code asset.ts#AssetInventory} 声明
 * {@code planName / matchedCount / surplusCount / remark} 四字段，
 * 而真实列是 {@code total_count / profit_count / loss_count}，且**无 remark 列**。
 * 同时 {@code views/asset/inventory/AssetInventoryList.vue} 整个模板只有一句
 * {@code <el-empty description="资产盘点功能开发中，请联系管理员" />} ——
 * 逐个 grep 页面引用：{@code planName/matchedCount/surplusCount/remark/totalCount}
 * **引用行数全部为 0**。
 *
 * <p>⇒ 按 AGENTS §4.5 第 39 条判据（「先 grep 页面有没有真的读它」）：
 * 页面**根本没实现**（空壳占位），不是「读了但列不存在」。
 * 故处置为：VO 按**真实列**生成，前端 interface 按真实列**更正**，
 * 并在此显式登记「资产盘点前端尚未实现」这一事实 ——
 * 不把「功能未实现」伪装成「契约陈旧」，也不反过来。
 *
 * <p><b>刻意不外露</b>：{@code deleted}/{@code enterpriseId}、
 * {@code updatedBy}（⚠️ Entity 里标了 {@code @TableField(exist = false)}，
 * 是**幽灵字段**，DB 无此列、读回恒 null，AGENTS §4.2 第 10 条）。
 *
 * @see AGENTS.md §0「出参面 Entity 直出」待办
 */
@Data
public class AssetInventoryVO {

    private Long id;
    private String inventoryNo;
    private LocalDate inventoryDate;
    private String period;
    private String status;
    private Integer totalCount;
    private Integer profitCount;
    private Integer lossCount;
    private Long voucherId;

    public static AssetInventoryVO from(AssetInventoryEntity e) {
        if (e == null) {
            return null;
        }
        AssetInventoryVO vo = new AssetInventoryVO();
        vo.setId(e.getId());
        vo.setInventoryNo(e.getInventoryNo());
        vo.setInventoryDate(e.getInventoryDate());
        vo.setPeriod(e.getPeriod());
        vo.setStatus(e.getStatus());
        vo.setTotalCount(e.getTotalCount());
        vo.setProfitCount(e.getProfitCount());
        vo.setLossCount(e.getLossCount());
        vo.setVoucherId(e.getVoucherId());
        return vo;
    }

    public static List<AssetInventoryVO> from(List<AssetInventoryEntity> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(AssetInventoryVO::from).collect(java.util.stream.Collectors.toList());
    }

    /** Entity 分页 → VO 分页；只换 records 元素类型，分页元信息原样透传 */
    public static IPage<AssetInventoryVO> from(IPage<AssetInventoryEntity> page) {
        if (page == null) {
            return null;
        }
        Page<AssetInventoryVO> out = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(from(page.getRecords()));
        return out;
    }
}