package com.huicai.sme.salary.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * SPEC-P143（D-4）：工资单明细 —— 纵表（每员工每项目一行，uq_salary_bill_item）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_salary_bill_item")
public class SalaryBillItemEntity extends BaseEntity {

    private Long billId;
    private Long employeeId;
    private Long itemId;
    private BigDecimal amount;
}
