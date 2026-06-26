package com.huicai.module.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_arap_settlement")
public class ArapSettlementEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String settlementNo;
    private String settlementType;
    private LocalDate settlementDate;
    private String period;
    private Long partyId;
    private String partyType;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private Long voucherId;
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
