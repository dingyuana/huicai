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
    private Long receivableId;
    private Long payableId;
    /** 业务单据ID（P34 替代 receivableId/payableId） */
    private Long businessDocId;
    private BigDecimal settledAmount;
    private BigDecimal discountAmount;

    /** 核销前单据余额快照 — DB 无此列 */
    @TableField(exist = false)
    private BigDecimal beforeBalance;

    /** 核销后单据余额快照 — DB 无此列 */
    @TableField(exist = false)
    private BigDecimal afterBalance;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
