package com.huicai.sme.salary.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * SPEC-P143：工资单主表 —— 每企业每期间一张（uq_salary_bill_period）。
 *
 * <p>状态机 {@code DRAFT --confirm(计提凭证)--> CONFIRMED --pay(发放凭证)--> PAID}；
 * 六个合计列由 confirm 时逐员工汇总回填；{@code voucherId}/{@code payVoucherId}
 * 是两张凭证的回填引用（A-3 @ReferenceGuard 登记，凭证删除被守卫拦截）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_salary_bill")
public class SalaryBillEntity extends BaseEntity {

    /** yyyyMM，与凭证 period 同口径 */
    private String period;
    private String status;
    private BigDecimal totalGross;
    private BigDecimal totalSocial;
    private BigDecimal totalSpecialAdd;
    private BigDecimal totalOtherDeduct;
    private BigDecimal totalTax;
    private BigDecimal totalNet;
    /** 确认（计提）凭证 */
    private Long voucherId;
    /** 发放凭证 */
    private Long payVoucherId;
    private String remark;
}
