package com.huicai.sme.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_arap_settlement_entry")
public class ArapSettlementEntryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long settlementId;
    private Long receivableId;
    private Long payableId;
    /** 业务单据ID（P34 替代 receivableId/payableId） */
    private Long businessDocId;
    private BigDecimal settledAmount;
    private BigDecimal discountAmount;

    /** 核销前单据余额快照 */
    private BigDecimal beforeBalance;

    /** 核销后单据余额快照 */
    private BigDecimal afterBalance;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
