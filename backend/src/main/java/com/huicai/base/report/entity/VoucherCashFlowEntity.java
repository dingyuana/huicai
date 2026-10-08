package com.huicai.base.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 凭证现金流量分配实体（t_voucher_cash_flow）
 * P96 REQ-095：凭证级现金流项目绑定，支撑现金流量表精确取数
 */
@Data
@TableName("t_voucher_cash_flow")
public class VoucherCashFlowEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long voucherId;

    /** OPERATING_IN/OUT, INVESTING_IN/OUT, FINANCING_IN/OUT */
    private String flowType;

    private BigDecimal amount;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;

    private Long enterpriseId;
}
