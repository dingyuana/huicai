package com.huicai.sme.salary.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * SPEC-P143：工资单原始明细行出参（编辑草稿回显用）。
 *
 * <p>confirm 后的员工级快照走 {@link SalaryBillDetailVO}；本 VO 是保存前的
 * 纵表明细（每员工每项目一行），名称字段由服务层回填便于直接展示。</p>
 */
@Data
public class SalaryBillItemVO {

    private Long employeeId;

    private String employeeName;

    private Long itemId;

    private String itemName;

    private BigDecimal amount;
}
