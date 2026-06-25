package com.huicai.module.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 预付款/预收款实体 — 供应商预付或客户预收.
 */
@Data
@TableName("t_prepayment")
public class PrepaymentEntity implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long vendorId;

    private Long customerId;

    private Long docId;

    private Long voucherId;

    private String period;

    private LocalDate txDate;

    /** 预付款金额 */
    private BigDecimal amount;

    /** 已核销金额 */
    private BigDecimal settledAmount;

    /** 未核销金额 */
    private BigDecimal unsettledAmount;

    /** 摘要 */
    private String summary;

    /** 状态: DRAFT / SUBMITTED / AUDITED / POSTED */
    @StatusChangeable(entity = "PREPAYMENT", fieldName = "status")
    private String status;

    /** 关联单据类型 (如 bank_txn) */
    private String sourceDocType;

    /** 关联单据 ID */
    private Long sourceDocId;

    /** 备注 */
    private String remark;

    private String createdBy;

    private LocalDate createdAt;

    private LocalDate updatedAt;
}