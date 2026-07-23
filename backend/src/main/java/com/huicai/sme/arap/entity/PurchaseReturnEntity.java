package com.huicai.sme.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购退货记录（P53 M3）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_purchase_return")
public class PurchaseReturnEntity extends BaseEntity {

    private String returnNo;

    private Long vendorId;

    private String originalDocNo;

    private Long originalDocId;

    private BigDecimal returnAmount;

    private BigDecimal taxAmount;

    private String reason;

    private String status;

    private Long voucherId;

    private String voucherNo;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}