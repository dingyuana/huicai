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

    /**
     * 🔴 SPEC-P153 同型缺陷（与 Subject 同根）：漏 fill ⇒ INSERT 不带列
     * ⇒ DEFAULT 1 兜底 ⇒ 企业 2 的凭证现金流静默挂到企业 1 名下
     * （RLS 下企业 2 永远查不到自己的现金流量 = 静默数据丢失）。
     */
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private Long enterpriseId;
}
