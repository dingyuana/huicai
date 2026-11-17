package com.huicai.sme.salary.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * SPEC-P143（D-1）：员工级汇总与个税快照 —— confirm 时计算落库。
 *
 * <p>按员工聚合明细（纵表）+ 累计预扣个税计算结果。本表同时是
 * <b>个税累计台账的数据源</b>（本年已确认工资单逐员工汇总，不另设冗余台账），
 * 也是 D-3 员工自助查询（后置）的数据面。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_salary_bill_employee")
public class SalaryBillEmployeeEntity extends BaseEntity {

    private Long billId;
    private Long employeeId;
    /** 应发（全部 EARN 项） */
    private BigDecimal gross;
    /** 计税应发（EARN 且 is_taxable=true） */
    private BigDecimal taxableGross;
    /** 个人社保公积金（专项扣除） */
    private BigDecimal social;
    /** 专项附加扣除（只进个税公式） */
    private BigDecimal specialAdd;
    /** 其他扣款 */
    private BigDecimal otherDeduct;
    /** SPEC-P155 R1：单位承担社保（按比例自动算，进 SALC 费用分摊与 2211 贷方，不扣员工钱） */
    private BigDecimal companySocial;
    /** 本月应预扣个税 */
    private BigDecimal tax;
    /** 实发 = gross - social - otherDeduct - tax */
    private BigDecimal net;
}
