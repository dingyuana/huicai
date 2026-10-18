package com.huicai.base.business.dto.vo;

import com.huicai.base.business.entity.ArapSettlementEntryEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 核销单明细出参 VO（P102 出参面批次 15）
 *
 * <p><b>字段集 = {@code t_arap_settlement_entry} 的真实列</b>（V1 基线实测 7 列：
 * {@code id / settlement_id / business_doc_id / settled_amount / discount_amount / created_at}，
 * 外加 V103~V127 补的 {@code enterprise_id} 等隔离/审计列）。
 *
 * <p>🔴 <b>前端 SettlementPanel.vue 渲染的 5 列里，有 4 个后端不存在</b> ——
 * 该「核销依据」表格的列定义是：
 * <pre>
 *   来源单据   prop="sourceDocNo"    ← Entity 与 DB 均无此列
 *   目标单据   prop="targetDocNo"    ← Entity 与 DB 均无此列
 *   核销金额   row.settledAmount     ← 真实列 ✔
 *   核销前余额 row.beforeBalance    ← Entity 标了 exist=false（幽灵字段，AGENTS §4.2 第 10 条）
 *   核销后余额 row.afterBalance     ← Entity 标了 exist=false（幽灵字段）
 * </pre>
 * 其中 {@code beforeBalance/afterBalance} 更隐蔽：{@code ArapSettlementServiceImpl:487-488}
 * 的反核销路径<b>真的在读它们</b>（{@code reverseEntry.setBeforeBalance(entry.getBeforeBalance())}），
 * 但因为它们是幽灵字段，<b>源对象里恒为 null ⇒ 反冲时写回去也恒为 null</b>，
 * 读着像「有快照逻辑」，实则恒空。
 *
 * <p>本 VO <b>只暴露真实列</b>，并把上述 4 个字段显式注释在下方，
 * <b>不靠返回 null 假装功能正常</b>（AGENTS §4.5 第 39 条）。
 * 补齐它们需要独立立项：① 「核销前/后余额快照」需 DDL 加列或在核销时实时算
 * ② 「来源/目标单据号」需按 {@code business_doc_id} JOIN {@code t_business_doc.doc_no}
 * （铁律 #9 的编号关联溯源）。
 *
 * <p><b>刻意不外露</b>：{@code deleted}/{@code enterpriseId}（内部状态与租户维度）、
 * {@code createdAt}/{@code updatedBy}/{@code version}（审计列，
 * 且 grep SettlementPanel.vue 引用行数为 0 —— 页面一个都不读）。
 *
 * @see ArapSettlementVO
 * @see AGENTS.md §0「出参面 Entity 直出」待办
 */
@Data
public class ArapSettlementEntryVO {

    private Long id;
    private Long settlementId;
    /** 业务单据ID（P34 起替代已废弃的 receivableId/payableId） */
    private Long businessDocId;
    private BigDecimal settledAmount;
    private BigDecimal discountAmount;

    // ── 下面 4 个是前端 SettlementPanel.vue 在渲染、但后端尚未实现的字段 ──
    // ⚠️ sourceDocNo  — 来源单据号。需按 businessDocId JOIN t_business_doc.doc_no（铁律 #9）
    // ⚠️ targetDocNo  — 目标单据号。t_arap_settlement_entry 无「目标」概念（明细只挂业务单），
    //                    需先定义「核销双方」的数据模型，属独立立项
    // ⚠️ beforeBalance — 核销前余额快照。Entity 标了 @TableField(exist=false)，DB 无此列，
    //                     读回恒 null；且 ArapSettlementServiceImpl:487 的反核销路径在读它（恒空）
    // ⚠️ afterBalance  — 同上
    //
    // 完整实现需要：① 余额快照的 DDL 或实时计算 ② 单据号 JOIN ③ 前端列名对齐。
    // 本轮只做 VO 化，不填补功能缺口，也不用兜底值把「空」伪装成「0」（§4.5 第 39 条）。

    public static ArapSettlementEntryVO from(ArapSettlementEntryEntity e) {
        if (e == null) {
            return null;
        }
        ArapSettlementEntryVO vo = new ArapSettlementEntryVO();
        vo.setId(e.getId());
        vo.setSettlementId(e.getSettlementId());
        vo.setBusinessDocId(e.getBusinessDocId());
        vo.setSettledAmount(e.getSettledAmount());
        vo.setDiscountAmount(e.getDiscountAmount());
        return vo;
    }

    public static List<ArapSettlementEntryVO> from(List<ArapSettlementEntryEntity> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(ArapSettlementEntryVO::from).collect(java.util.stream.Collectors.toList());
    }
}