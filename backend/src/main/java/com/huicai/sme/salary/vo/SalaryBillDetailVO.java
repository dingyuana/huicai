package com.huicai.sme.salary.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/** SPEC-P143：工资单详情出参（继承列表行字段 + 员工级明细含个税快照回显）。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SalaryBillDetailVO extends SalaryBillVO {

    private List<EmployeeRowVO> employees;

    @Data
    public static class EmployeeRowVO {
        private Long employeeId;
        private String employeeName;
        private java.math.BigDecimal gross;
        private java.math.BigDecimal taxableGross;
        private java.math.BigDecimal social;
        private java.math.BigDecimal specialAdd;
        private java.math.BigDecimal otherDeduct;
        /** SPEC-P155：单位社保（自动算，不扣员工钱） */
        private java.math.BigDecimal companySocial;
        private java.math.BigDecimal tax;
        private java.math.BigDecimal net;
    }
}
