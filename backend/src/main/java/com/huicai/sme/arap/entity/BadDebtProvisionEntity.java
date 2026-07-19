package com.huicai.sme.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_bad_debt_provision")
public class BadDebtProvisionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String period;
    private String method;
    private LocalDate provisionDate;
    private BigDecimal totalAmount;
    private Long voucherId;
    private String voucherNo;
    @StatusChangeable(entity = "BAD_DEBT_PROVISION", fieldName = "status")
    private String status;
    private String remark;
    private Long createdBy;
    @TableField(exist = false)
    private Long updatedBy;

    // ===== P43 新增字段 =====
    /** 应有余额（按账龄计算的总坏账准备应有金额） */
    private BigDecimal expectedBalance;
    /** 科目已有余额（科目1231当前余额） */
    private BigDecimal existingBalance;
    /** 补提/冲回金额 = expectedBalance - existingBalance */
    private BigDecimal adjustmentAmount;
    /** 调整类型：PROVISION-补提, REVERSAL-冲回 */
    private String adjustmentType;
    /** 使用的计提方案ID */
    private Long schemeId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}