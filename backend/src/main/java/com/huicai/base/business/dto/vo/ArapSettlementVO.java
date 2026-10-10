package com.huicai.base.business.dto.vo;

import com.huicai.base.business.entity.ArapSettlementEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 核销单出参 VO（P102 出参面批次 15）
 *
 * <p>⚠️ <b>本类原为 {@code extends ArapSettlementEntity}，已改为独立 VO</b> ——
 * 「继承式 VO」会让<b>隔离形同虚设</b>：VO 继承了 Entity 的<b>全部</b>字段
 * （{@code deleted / enterpriseId / version / createdBy / updatedBy …}），
 * 而 {@code MasterDataVoContractTest} 用 {@code getDeclaredFields()} 比对，
 * <b>看不见继承来的字段</b> ⇒ 守卫报绿、实际内部字段照旧外露，
 * 铁律 #13 形同未收口（AGENTS §4.5 第 16 条「反向缺口」在测试侧的同型：
 * <b>扫描口径覆盖不全</b>）。
 *
 * <p>该盲区由 {@code OutParamEntityStructureTest} 实测暴露：它的
 * {@code isEntity()} 初版沿父类链找 {@code *Entity} 后缀，把本 VO 判成了 Entity，
 * 实测点数 60 而真实存量 58 ⇒ 顺着这条线索才发现「继承式 VO」这个更根本的问题。
 * **判据沉淀：写「某类不属于 X」的门禁时，判定函数若沿继承链找特征，
 * 就会把「继承自 X 的子类」误判 —— 必须只用类型自身的名字。**
 *
 * <p><b>字段集</b>：与前端 {@code frontend/src/api/modules/arapSettlement.ts#ArapSettlement}
 * 声明的字段逐一对齐，另加 Mapper 联表带出的 {@code customerName}/{@code vendorName}，
 * 由 {@code MasterDataVoContractTest} 锁死。
 *
 * <p>🔴 <b>⚠️ 前端原声明的 {@code remark} 已从契约删除，因后端无此列</b> ——
 * {@code t_arap_settlement} 实测 19 列里<b>没有 remark</b>（{@code information_schema} 核对），
 * 而 {@code SettlementPanel.vue} 同时存在「新建表单的备注输入框」与
 * 「详情页的备注展示位」⇒ 按AGENTS §4.5 第 39 条判据（页面真的在用）
 * 这属**功能未实现**而非契约陈旧，需要补列（DDL）才能真正修好。
 * 本轮只做「据实标注 + 契约对齐」，**不把「数据永远为空」伪装成正常数据**
 * （同 §4.5 第 39 条Prepayment 4 列的处理方式）。
 *
 * <p><b>刻意不外露</b>：{@code deleted}/{@code enterpriseId}（内部状态与租户维度）、
 * {@code createdBy}/{@code createdAt}/{@code updatedAt}（审计列 —— 且 grep
 * {@code SettlementPanel.vue} 的引用行数<b>全为 0</b>，页面一个都不读，
 * 故它们既非合法展示字段也不该进契约，AGENTS §4.5 第 38 条）、
 * {@code voucherNo} 与 {@code reversedFromSettlementId}（⚠️ Entity 里两者都标了
 * {@code exist = false}，是<b>幽灵字段</b> —— DB 无这些列、赋值不参与 SQL、读回恒 null，
 * AGENTS §4.2 第 10 条；本 VO 因此既不含它们，也不从 Entity 拷贝）。
 *
 * @see AGENTS.md §0「出参面 Entity 直出」待办
 */
@Data
public class ArapSettlementVO {

    private Long id;
    private String settlementNo;
    /** chk_settlement_type 允许 RECEIVE / PAY */
    private String settlementType;
    private LocalDate settlementDate;
    private String period;
    private Long partyId;
    /** CUSTOMER / VENDOR */
    private String partyType;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private Long voucherId;
    private String status;
    /** 来源单据类型/ID（铁律 #9 编号关联溯源） */
    private String sourceDocType;
    private Long sourceDocId;
    /** 备注（V175 补列，此前前端在用而后端无此列） */
    private String remark;

    /** 客户名称（partyType=CUSTOMER 时，Mapper 联表带出） */
    private String customerName;

    /** 供应商名称（partyType=VENDOR 时，Mapper 联表带出） */
    private String vendorName;

    public static ArapSettlementVO from(ArapSettlementEntity e) {
        if (e == null) {
            return null;
        }
        ArapSettlementVO vo = new ArapSettlementVO();
        vo.setId(e.getId());
        vo.setSettlementNo(e.getSettlementNo());
        vo.setSettlementType(e.getSettlementType());
        vo.setSettlementDate(e.getSettlementDate());
        vo.setPeriod(e.getPeriod());
        vo.setPartyId(e.getPartyId());
        vo.setPartyType(e.getPartyType());
        vo.setTotalAmount(e.getTotalAmount());
        vo.setDiscountAmount(e.getDiscountAmount());
        vo.setVoucherId(e.getVoucherId());
        vo.setStatus(e.getStatus());
        vo.setSourceDocType(e.getSourceDocType());
        vo.setSourceDocId(e.getSourceDocId());
        vo.setRemark(e.getRemark());
        return vo;
    }
}