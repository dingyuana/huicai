package com.huicai.sme.salary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.sme.salary.entity.SalaryBillEmployeeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface SalaryBillEmployeeMapper extends BaseMapper<SalaryBillEmployeeEntity> {

    /**
     * 个税累计口径（D-1 累计预扣法）：某员工本年、当前期间之前的已确认/已发放工资单的
     * <b>累计应纳税所得额运行值</b>与累计已预扣税额。
     *
     * <p>减除费用按<b>任职月份</b>口径：每张历史单据抵 5000（即该员工在本系统有工资单的月份），
     * 年中入职的员工不因「日历月份已过」被误扣（5000×日历月会导致少缴，年末汇算才能纠）。
     * 企业范围由 RLS 收敛（不手工拼 enterprise_id，VoucherTypeServiceImpl 同口径）。</p>
     */
    @Select("""
            SELECT COALESCE(SUM(e.taxable_gross), 0) - 5000 * COUNT(*)
                     - COALESCE(SUM(e.social), 0) - COALESCE(SUM(e.special_add), 0) AS cumTaxableRunning,
                   COALESCE(SUM(e.tax), 0) AS cumTax
            FROM t_salary_bill_employee e
            JOIN t_salary_bill b ON b.id = e.bill_id
            WHERE e.employee_id = #{employeeId} AND e.deleted = 0
              AND b.deleted = 0 AND b.status IN ('CONFIRMED','PAID')
              AND b.period LIKE #{yearPrefix} AND b.period < #{period}
            """)
    CumulativeRow selectCumulative(@Param("employeeId") Long employeeId,
                                   @Param("yearPrefix") String yearPrefix,
                                   @Param("period") String period);

    /** 累计值载体（MyBatis 按列别名装配）。 */
    class CumulativeRow {
        /** 截至上月的累计应纳税所得额（已扣减除费用/专项扣除/专项附加） */
        public BigDecimal cumTaxableRunning;
        /** 截至上月累计已预扣税额 */
        public BigDecimal cumTax;
    }
}
