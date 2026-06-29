package com.huicai.module.arap.entity;

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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
