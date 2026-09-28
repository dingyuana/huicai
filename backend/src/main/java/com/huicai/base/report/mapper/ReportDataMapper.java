package com.huicai.base.report.mapper;

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
        SELECT s.id, s.id AS subject_id, s.code, s.name, s.level, s.parent_id, s.direction,
               s.account_type,
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
     * 期间各损益段取数（利润表）。
     *
     * <p>P97/REQ-099：段位按企业会计准则逐段显式枚举，不再用 {@code code LIKE '6%'} 一锅端。
     * 旧口径三处错：① {@code revenue} 把贷方 6xx 全算进营业收入（营业外收入/其他收益/投资收益
     * 都被计入营收）② 6403 税金及附加整段缺失 → 利润总额虚高 ③ 6801 所得税缺失 → 无净利润。
     *
     * <p>段位与本项目科目表实证的 6xx 四位段一一对应，不依赖 direction 兜底：
     * 6001/6051 收入、6401/6402 成本、6403 税金、6601-6604 四费、6101/6111/6115/6117 收益类、
     * 6701 减值、6301/6711 营业外、6801 所得税。6901 以前年度损益调整不进利润表。
     */
    @Select("""
        SELECT
          SUM(CASE WHEN s.code LIKE '6001%' OR s.code LIKE '6051%' THEN e.credit - e.debit ELSE 0 END) AS revenue,
          SUM(CASE WHEN s.code LIKE '6401%' OR s.code LIKE '6402%' THEN e.debit - e.credit ELSE 0 END) AS cost,
          SUM(CASE WHEN s.code LIKE '6403%' THEN e.debit - e.credit ELSE 0 END) AS tax_and_surcharge,
          SUM(CASE WHEN s.code LIKE '6601%' THEN e.debit - e.credit ELSE 0 END) AS selling_expense,
          SUM(CASE WHEN s.code LIKE '6602%' THEN e.debit - e.credit ELSE 0 END) AS admin_expense,
          SUM(CASE WHEN s.code LIKE '6603%' THEN e.debit - e.credit ELSE 0 END) AS financial_expense,
          SUM(CASE WHEN s.code LIKE '6604%' THEN e.debit - e.credit ELSE 0 END) AS rd_expense,
          SUM(CASE WHEN s.code LIKE '6117%' THEN e.credit - e.debit ELSE 0 END) AS other_income,
          SUM(CASE WHEN s.code LIKE '6111%' THEN e.credit - e.debit ELSE 0 END) AS investment_income,
          SUM(CASE WHEN s.code LIKE '6101%' THEN e.credit - e.debit ELSE 0 END) AS fair_value_income,
          SUM(CASE WHEN s.code LIKE '6115%' THEN e.credit - e.debit ELSE 0 END) AS asset_disposal_income,
          SUM(CASE WHEN s.code LIKE '6701%' THEN e.debit - e.credit ELSE 0 END) AS asset_impairment_loss,
          SUM(CASE WHEN s.code LIKE '6301%' THEN e.credit - e.debit ELSE 0 END) AS non_operating_income,
          SUM(CASE WHEN s.code LIKE '6711%' THEN e.debit - e.credit ELSE 0 END) AS non_operating_expense,
          SUM(CASE WHEN s.code LIKE '6801%' THEN e.debit - e.credit ELSE 0 END) AS income_tax
        FROM t_voucher_entry e
        INNER JOIN t_voucher v ON v.id = e.voucher_id
        INNER JOIN t_subject s ON s.id = e.subject_id
        WHERE v.deleted = 0 AND v.status = 'POSTED'
          AND v.period = #{period}
          AND v.voucher_no NOT LIKE 'CLOSE-%' AND v.voucher_no NOT LIKE 'DISTRIB-%'
    """)
    Map<String, Object> incomeStatementData(@Param("period") String period);

    /**
     * 累计数据（从年初到本期），段位与 {@link #incomeStatementData} 逐段对齐。
     * 两处若不同步演进，本期与累计列会各自漂移到不同口径。
     */
    @Select("""
        SELECT
          SUM(CASE WHEN s.code LIKE '6001%' OR s.code LIKE '6051%' THEN e.credit - e.debit ELSE 0 END) AS cumulative_revenue,
          SUM(CASE WHEN s.code LIKE '6401%' OR s.code LIKE '6402%' THEN e.debit - e.credit ELSE 0 END) AS cumulative_cost,
          SUM(CASE WHEN s.code LIKE '6403%' THEN e.debit - e.credit ELSE 0 END) AS cumulative_tax_and_surcharge,
          SUM(CASE WHEN s.code LIKE '6601%' THEN e.debit - e.credit ELSE 0 END) AS cumulative_selling_expense,
          SUM(CASE WHEN s.code LIKE '6602%' THEN e.debit - e.credit ELSE 0 END) AS cumulative_admin_expense,
          SUM(CASE WHEN s.code LIKE '6603%' THEN e.debit - e.credit ELSE 0 END) AS cumulative_financial_expense,
          SUM(CASE WHEN s.code LIKE '6604%' THEN e.debit - e.credit ELSE 0 END) AS cumulative_rd_expense,
          SUM(CASE WHEN s.code LIKE '6117%' THEN e.credit - e.debit ELSE 0 END) AS cumulative_other_income,
          SUM(CASE WHEN s.code LIKE '6111%' THEN e.credit - e.debit ELSE 0 END) AS cumulative_investment_income,
          SUM(CASE WHEN s.code LIKE '6101%' THEN e.credit - e.debit ELSE 0 END) AS cumulative_fair_value_income,
          SUM(CASE WHEN s.code LIKE '6115%' THEN e.credit - e.debit ELSE 0 END) AS cumulative_asset_disposal_income,
          SUM(CASE WHEN s.code LIKE '6701%' THEN e.debit - e.credit ELSE 0 END) AS cumulative_asset_impairment_loss,
          SUM(CASE WHEN s.code LIKE '6301%' THEN e.credit - e.debit ELSE 0 END) AS cumulative_non_operating_income,
          SUM(CASE WHEN s.code LIKE '6711%' THEN e.debit - e.credit ELSE 0 END) AS cumulative_non_operating_expense,
          SUM(CASE WHEN s.code LIKE '6801%' THEN e.debit - e.credit ELSE 0 END) AS cumulative_income_tax
        FROM t_voucher_entry e
        INNER JOIN t_voucher v ON v.id = e.voucher_id
        INNER JOIN t_subject s ON s.id = e.subject_id
        WHERE v.deleted = 0 AND v.status = 'POSTED'
          AND v.period >= #{yearStart} AND v.period <= #{period}
          AND v.voucher_no NOT LIKE 'CLOSE-%' AND v.voucher_no NOT LIKE 'DISTRIB-%'
    """)
    Map<String, Object> cumulativeData(@Param("yearStart") String yearStart,
                                       @Param("period") String period);

    /**
     * P88③/P94：现金及现金等价物期初/期末余额，供现金流量表补"期初/期末现金余额"闭环 + 与净流量勾稽。
     *
     * <p>科目范围用白名单而非 {@code LIKE '100%'}：本项目科目表 1xxx 段实测只有
     * 1001 库存现金 / 1002 银行存款 / 1012 其他货币资金（1009 未使用），
     * 旧 LIKE 口径漏掉 1012 会让勾稽差异虚高。放宽到 LIKE 会把非货币资金科目误纳。
     */
    @Select("""
        SELECT COALESCE(SUM(sb.begin_balance), 0) AS begin_cash,
               COALESCE(SUM(sb.end_balance), 0)   AS end_cash
        FROM t_subject_balance sb
        INNER JOIN t_subject s ON s.id = sb.subject_id
        WHERE sb.period = #{period}
          AND s.code IN ('1001', '1002', '1009', '1012')
          AND s.deleted = 0
    """)
    Map<String, Object> cashSubjectBalance(@Param("period") String period);

    /**
     * 现金流量表(基于现金流分配)
     * 从凭证分录中银行存款(1002)的借贷发生额计算，按对方科目判断业务活动类型。
     * 对方科目为固定资产(1601)等投资类 → 投资活动；其余 → 经营活动。
     */
    @Select("""
        SELECT flow_type, SUM(amount) AS amount
        FROM (
            SELECT
              CASE
                WHEN e.debit > 0 THEN
                  CASE WHEN EXISTS (
                    SELECT 1 FROM t_voucher_entry e2
                    INNER JOIN t_subject s2 ON s2.id = e2.subject_id
                    WHERE e2.voucher_id = e.voucher_id AND e2.id != e.id
                      AND e2.credit > 0
                      AND (s2.code LIKE '15%' OR s2.code LIKE '16%' OR s2.code LIKE '17%' OR s2.code LIKE '18%' OR s2.code LIKE '19%')
                  ) THEN 'INVESTING_IN' ELSE 'OPERATING_IN' END
                ELSE
                  CASE WHEN EXISTS (
                    SELECT 1 FROM t_voucher_entry e2
                    INNER JOIN t_subject s2 ON s2.id = e2.subject_id
                    WHERE e2.voucher_id = e.voucher_id AND e2.id != e.id
                      AND e2.debit > 0
                      AND (s2.code LIKE '15%' OR s2.code LIKE '16%' OR s2.code LIKE '17%' OR s2.code LIKE '18%' OR s2.code LIKE '19%')
                  ) THEN 'INVESTING_OUT' ELSE 'OPERATING_OUT' END
              END AS flow_type,
              CASE WHEN e.debit > 0 THEN e.debit ELSE e.credit END AS amount
            FROM t_voucher_entry e
            INNER JOIN t_voucher v ON v.id = e.voucher_id
            INNER JOIN t_subject s ON s.id = e.subject_id
            WHERE v.deleted = 0 AND v.status = 'POSTED'
              AND v.period >= #{startPeriod} AND v.period <= #{endPeriod}
              AND s.code LIKE '1002%'
        ) t
        WHERE flow_type IS NOT NULL
        GROUP BY flow_type
    """)
    List<Map<String, Object>> cashFlowData(@Param("startPeriod") String startPeriod,
                                           @Param("endPeriod") String endPeriod);

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
