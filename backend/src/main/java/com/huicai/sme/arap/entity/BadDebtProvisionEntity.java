package com.huicai.sme.arap.entity;

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
@TableName("t_bad_debt_provision")
public class BadDebtProvisionEntity extends BaseEntity {

    private String period;
    private String method;
    private LocalDate provisionDate;
    private BigDecimal totalAmount;
    private Long voucherId;
    /** 凭证编号 — V194 真实列（核销制证后回写，此前写了不落库） */
    @TableField("voucher_no")
    private String voucherNo;
    @StatusChangeable(entity = "BAD_DEBT_PROVISION", fieldName = "status")
    private String status;
    private String remark;
    private Long createdBy;
    /* SPEC-P153：updated_by 真实列存在（此前误覆盖 exist=false）*/

    // ===== P43 新增字段 =====
    /** 应有余额（按账龄计算的总坏账准备应有金额）— V194 真实列（计提依据快照） */
    @TableField("expected_balance")
    private BigDecimal expectedBalance;
    /** 科目已有余额（科目1231当前余额）— V194 真实列 */
    @TableField("existing_balance")
    private BigDecimal existingBalance;
    /** 补提/冲回金额 = expectedBalance - existingBalance — V194 真实列 */
    @TableField("adjustment_amount")
    private BigDecimal adjustmentAmount;
    /** 调整类型：PROVISION-补提, REVERSAL-冲回 — V194 真实列 */
    @TableField("adjustment_type")
    private String adjustmentType;
    /**
     * 使用的计提方案ID — 保持幽灵字段（SPEC-P153 定性）：真写方是
     * SchemeItemEntity.scheme_id（真实列），扫描器把 item.setSchemeId 误配给
     * provision；provision 无此写方，不建列（凭空空列）。
     */
    @TableField(exist = false)
    private Long schemeId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}