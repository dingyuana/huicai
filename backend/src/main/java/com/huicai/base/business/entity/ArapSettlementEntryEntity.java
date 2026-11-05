package com.huicai.base.business.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_arap_settlement_entry")
public class ArapSettlementEntryEntity extends BaseEntity {

    private Long settlementId;
    /** P34 移除 — 使用 businessDocId 替代 */
    @TableField(exist = false)
    private Long receivableId;
    /** P34 移除 — 使用 businessDocId 替代 */
    @TableField(exist = false)
    private Long payableId;
    /** 业务单据ID（P34 替代 receivableId/payableId） */
    private Long businessDocId;
    private BigDecimal settledAmount;
    private BigDecimal discountAmount;

    /**
     * 核销前单据余额快照 — V194 真实列。
     * SPEC-P153 取证：ReconciliationServiceImpl#execute L462 正向写入、
     * ArapSettlementServiceImpl 反核销 L510 读回拷贝 —— 读方在，此前恒 null。
     */
    @TableField("before_balance")
    private BigDecimal beforeBalance;

    /** 核销后单据余额快照 — V194 真实列（对账审计依据） */
    @TableField("after_balance")
    private BigDecimal afterBalance;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
