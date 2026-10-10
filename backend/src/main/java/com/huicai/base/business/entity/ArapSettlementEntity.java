package com.huicai.base.business.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_arap_settlement")
public class ArapSettlementEntity extends BaseEntity {

    private String settlementNo;
    private String settlementType;
    private LocalDate settlementDate;
    private String period;
    private Long partyId;
    private String partyType;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private Long voucherId;

    /** 来源单据类型: RECEIPT/PAYMENT/bank_txn 等 (V140) */
    private String sourceDocType;

    /** 来源单据ID（收付款业务单/银行流水）(V140) */
    private Long sourceDocId;
    /**
     * 备注 —— V175 补列。
 *
     * <p>此前前端 {@code views/arap/reconciliation/SettlementPanel.vue} 有「新建备注输入框」
     * （:88）与「详情备注展示位」（:115）两处真的在用，而后端无此列
     * ⇒ 备注提交后无处落库、详情恒显 {@code '-'}。
     * 按 AGENTS §4.5 第 39 条判据（页面真的在用 ⇒ 必须接上真实数据）补列，
     * 而非从前端契约里删掉这个字段。
     */
    private String remark;

    /**
     * 凭证编号（冗余存储，用于快速查询）— DB 无此列
     */
    @TableField(exist = false)
    private String voucherNo;

    /** 被对冲的原核销单ID（红冲时指向原单）— DB 无此列 */
    @TableField(value = "reversed_from_settlement_id", exist = false)
    private Long reversedFromSettlementId;
    @StatusChangeable(entity = "ARAP_SETTLEMENT", fieldName = "status")
    private String status;
    private Long createdBy;
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}