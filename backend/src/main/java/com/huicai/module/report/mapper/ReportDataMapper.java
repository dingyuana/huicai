package com.huicai.module.report.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface ReportDataMapper {

    /**
     * 科目余额表数据(按期间)
     */
    @Select("""
        SELECT s.id, s.code, s.name, s.level, s.parent_id, s.direction,
               COALESCE(sb.begin_balance, 0)   AS begin_balance,
               COALESCE(sb.debit_total, 0)     AS debit_total,
               COALESCE(sb.credit_total, 0)    AS credit_total,
               COALESCE(sb.end_balance, 0)     AS end_balance
        FROM t_subject s
        LEFT JOIN t_subject_balance sb
               ON sb.subject_id = s.id AND sb.period = #{period}
        WHERE s.deleted = 0 AND s.is_active = TRUE
        ORDER BY s.code
    """)
    List<Map<String, Object>> subjectBalance(@Param("period") String period);

    /**
     * 期间借方/贷方合计(利润表)
     */
    @Select("""
        SELECT
          SUM(CASE WHEN s.direction = 'credit' AND s.code LIKE '6%' THEN e.credit - e.debit ELSE 0 END) AS revenue,
          SUM(CASE WHEN s.direction = 'debit'  AND s.code LIKE '6%' THEN e.debit  - e.credit ELSE 0 END) AS revenue_offset,
          SUM(CASE WHEN s.code LIKE '6401%' OR s.code LIKE '6402%' THEN e.debit - e.credit ELSE 0 END) AS cost,
          SUM(CASE WHEN s.code LIKE '6601%' OR s.code LIKE '6602%' OR s.code LIKE '6603%' THEN e.debit - e.credit ELSE 0 END) AS expense,
          SUM(CASE WHEN s.code LIKE '6604%' OR s.code LIKE '6605%' OR s.code LIKE '6606%' OR s.code LIKE '6607%' OR s.code LIKE '6608%' OR s.code LIKE '6609%' OR s.code LIKE '6610%' OR s.code LIKE '6611%' OR s.code LIKE '6612%' OR s.code LIKE '6613%' OR s.code LIKE '6614%' OR s.code LIKE '6615%' OR s.code LIKE '6616%' OR s.code LIKE '6617%' OR s.code LIKE '6701%' OR s.code LIKE '6711%' THEN e.debit - e.credit ELSE 0 END) AS other_expense,
          SUM(CASE WHEN s.code LIKE '6%' THEN e.credit - e.debit ELSE 0 END) AS total_revenue,
          SUM(CASE WHEN s.code LIKE '6%' THEN e.debit - e.credit ELSE 0 END) AS total_cost_expense
        FROM t_voucher_entry e
        INNER JOIN t_voucher v ON v.id = e.voucher_id
        INNER JOIN t_subject s ON s.id = e.subject_id
        WHERE v.deleted = 0 AND v.status = 'POSTED'
          AND v.period = #{period}
    """)
    Map<String, Object> incomeStatementData(@Param("period") String period);

    /**
     * 累计数据(从年初到本期)
     */
    @Select("""
        SELECT
          SUM(CASE WHEN s.code LIKE '6%' THEN e.credit - e.debit ELSE 0 END) AS cumulative_revenue,
          SUM(CASE WHEN s.code LIKE '6401%' OR s.code LIKE '6402%' THEN e.debit - e.credit ELSE 0 END) AS cumulative_cost,
          SUM(CASE WHEN s.code LIKE '6%' THEN e.debit - e.credit ELSE 0 END) AS cumulative_cost_expense
        FROM t_voucher_entry e
        INNER JOIN t_voucher v ON v.id = e.voucher_id
        INNER JOIN t_subject s ON s.id = e.subject_id
        WHERE v.deleted = 0 AND v.status = 'POSTED'
          AND v.period >= #{yearStart} AND v.period <= #{period}
    """)
    Map<String, Object> cumulativeData(@Param("yearStart") String yearStart,
                                       @Param("period") String period);

    /**
     * 现金流量表(基于现金流分配)
     */
    @Select("""
        SELECT cf.flow_type, SUM(cf.amount) AS amount
        FROM t_voucher_cash_flow cf
        INNER JOIN t_voucher v ON v.id = cf.voucher_id
        WHERE v.deleted = 0 AND v.status = 'POSTED'
          AND v.period = #{period}
        GROUP BY cf.flow_type
    """)
    List<Map<String, Object>> cashFlowData(@Param("period") String period);

    /**
     * 资产/负债/权益数据
     */
    @Select("""
        SELECT
          SUM(CASE WHEN s.code LIKE '1%' THEN
            CASE WHEN s.direction = 'debit' THEN sb.end_balance ELSE -sb.end_balance END
            ELSE 0 END) AS total_assets,
          SUM(CASE WHEN s.code LIKE '14%' OR s.code LIKE '15%' THEN
            CASE WHEN s.direction = 'debit' THEN sb.end_balance ELSE -sb.end_balance END
            ELSE 0 END) AS current_assets,
          SUM(CASE WHEN s.code LIKE '16%' THEN
            CASE WHEN s.direction = 'debit' THEN sb.end_balance ELSE -sb.end_balance END
            ELSE 0 END) AS fixed_assets,
          SUM(CASE WHEN s.code LIKE '2%' OR s.code LIKE '3%' OR s.code LIKE '4%' OR s.code LIKE '5%' THEN
            CASE WHEN s.direction = 'credit' THEN sb.end_balance ELSE -sb.end_balance END
            ELSE 0 END) AS total_liab_eq
        FROM t_subject_balance sb
        INNER JOIN t_subject s ON s.id = sb.subject_id
        WHERE s.deleted = 0 AND sb.period = #{period}
    """)
    Map<String, Object> balanceSheetAggregate(@Param("period") String period);

    /**
     * 趋势数据(多期)
     */
    @Select("""
        SELECT v.period,
               SUM(CASE WHEN s.code LIKE '6%' THEN e.credit - e.debit ELSE 0 END) AS revenue,
               SUM(CASE WHEN s.code LIKE '6401%' OR s.code LIKE '6402%' THEN e.debit - e.credit ELSE 0 END) AS cost,
               SUM(CASE WHEN s.code LIKE '6%' THEN e.debit - e.credit ELSE 0 END) AS expense
        FROM t_voucher_entry e
        INNER JOIN t_voucher v ON v.id = e.voucher_id
        INNER JOIN t_subject s ON s.id = e.subject_id
        WHERE v.deleted = 0 AND v.status = 'POSTED'
          AND v.period >= #{startPeriod} AND v.period <= #{endPeriod}
        GROUP BY v.period
        ORDER BY v.period
    """)
    List<Map<String, Object>> trendData(@Param("startPeriod") String startPeriod,
                                         @Param("endPeriod") String endPeriod);
}
