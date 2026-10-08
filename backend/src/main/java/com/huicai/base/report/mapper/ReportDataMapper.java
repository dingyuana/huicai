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
     * P96 REQ-095：增加筹资活动（FINANCING）分支——借款/还款（2001/2501）、
     *   资本注入（4001）、股利支付（4104）；修正预付账款（1123）设备款归投资活动。
     * 对方科目为固定资产(1601)等投资类 → 投资活动；
     * 对方科目为借款/实收资本/利润分配 → 筹资活动；
     * 其余 → 经营活动。
     */
    @Select("""
        SELECT flow_type, SUM(amount) AS amount
        FROM (
            SELECT
              CASE
                WHEN e.debit > 0 THEN
                  CASE
                    WHEN EXISTS (
                      SELECT 1 FROM t_voucher_entry e2
                      INNER JOIN t_subject s2 ON s2.id = e2.subject_id
                      WHERE e2.voucher_id = e.voucher_id AND e2.id != e.id
                        AND e2.credit > 0
                        AND (s2.code LIKE '15%' OR s2.code LIKE '16%' OR s2.code LIKE '17%' OR s2.code LIKE '18%' OR s2.code LIKE '19%')
                    ) THEN 'INVESTING_IN'
                    WHEN EXISTS (
                      SELECT 1 FROM t_voucher_entry e2
                      INNER JOIN t_subject s2 ON s2.id = e2.subject_id
                      WHERE e2.voucher_id = e.voucher_id AND e2.id != e.id
                        AND e2.credit > 0
                        AND (s2.code LIKE '2001%' OR s2.code LIKE '2501%' OR s2.code LIKE '4001%' OR s2.code LIKE '4101%')
                    ) THEN 'FINANCING_IN'
                    ELSE 'OPERATING_IN'
                  END
                ELSE
                  CASE
                    WHEN EXISTS (
                      SELECT 1 FROM t_voucher_entry e2
                      INNER JOIN t_subject s2 ON s2.id = e2.subject_id
                      WHERE e2.voucher_id = e.voucher_id AND e2.id != e.id
                        AND e2.debit > 0
                        AND (s2.code LIKE '15%' OR s2.code LIKE '16%' OR s2.code LIKE '17%' OR s2.code LIKE '18%' OR s2.code LIKE '19%' OR s2.code LIKE '1123%')
                    ) THEN 'INVESTING_OUT'
                    WHEN EXISTS (
                      SELECT 1 FROM t_voucher_entry e2
                      INNER JOIN t_subject s2 ON s2.id = e2.subject_id
                      WHERE e2.voucher_id = e.voucher_id AND e2.id != e.id
                        AND e2.debit > 0
                        AND (s2.code LIKE '2001%' OR s2.code LIKE '2501%' OR s2.code LIKE '4104%')
                    ) THEN 'FINANCING_OUT'
                    ELSE 'OPERATING_OUT'
                  END
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
     * P96 REQ-096：间接法补充资料调节项。
     * 取累计折旧(1602)/累计摊销(1702)/长期待摊(1801)的本期贷方发生额（非付现费用），
     * 以及经营性应收(1122 应收账款)、应付(2202 应付账款)、存货(1403/1405/5001)的期初期末变动。
     * 净利润从利润表取，不在此查询。
     */
    @Select("""
        SELECT
          (SELECT COALESCE(SUM(e.credit), 0) FROM t_voucher_entry e
             INNER JOIN t_voucher v ON v.id = e.voucher_id
             INNER JOIN t_subject s ON s.id = e.subject_id
            WHERE v.deleted = 0 AND v.status = 'POSTED'
              AND v.period = #{period} AND s.code IN ('1602','1702','1801')) AS non_cash_expense,
          (SELECT COALESCE(SUM(e.debit), 0) FROM t_voucher_entry e
             INNER JOIN t_voucher v ON v.id = e.voucher_id
             INNER JOIN t_subject s ON s.id = e.subject_id
            WHERE v.deleted = 0 AND v.status = 'POSTED'
              AND v.period = #{period} AND s.code = '6603') AS financial_expense,
          (SELECT COALESCE(end_balance - begin_balance, 0) FROM t_subject_balance sb
             INNER JOIN t_subject s ON s.id = sb.subject_id
            WHERE sb.period = #{period} AND s.code = '1122') AS ar_change,
          (SELECT COALESCE(end_balance - begin_balance, 0) FROM t_subject_balance sb
             INNER JOIN t_subject s ON s.id = sb.subject_id
            WHERE sb.period = #{period} AND s.code = '2202') AS ap_change,
          (SELECT COALESCE(end_balance - begin_balance, 0) FROM t_subject_balance sb
             INNER JOIN t_subject s ON s.id = sb.subject_id
            WHERE sb.period = #{period} AND s.code IN ('1403','1405','5001')) AS inventory_change
    """)
    Map<String, Object> indirectMethodAdjustments(@Param("period") String period);

    /**
     * 辅助核算明细（P97/REQ-097，阶段 C-2）。
     *
     * <p>按 <b>整个 assist_json 值</b>分组，不按 customerName/vendorId 之类的具体键取值：
     * 本项目 assist_json 全链路透传，代码库中无任何一处定义其 schema，按臆测键名写 SQL
     * 会匹配不到真实数据，且上游键名一旦不同就静默返回空。jsonb 等值分组对键名透明，
     * 原始 JSON 原样返回交前端通用渲染。
     *
     * <p>只收「科目配了 aux_calc_type」且「分录带 assist_json」的行：两者缺一就无法归入某个
     * 辅助项，强行归集会造成明细之和与科目合计对不上。
     *
     * <p>本期发生额 = 本期间；累计发生额 = 年初至本期（年初 = period 的年份 + 01）。
     *
     * <p>别名一律 snake_case，与本 Mapper 其他 @Select 一致——Postgres 会把未加引号的别名
     * 折成小写（{@code AS subjectCode} 实际得到 {@code subjectcode}），写驼峰必须加引号，易漏。
     */
    @Select("""
        SELECT s.code                                   AS subject_code,
               s.name                                   AS subject_name,
               s.aux_calc_type                          AS aux_calc_type,
               cur.assist_json::text                    AS assist_json,
               COALESCE(SUM(cur.debit), 0)              AS debit_total,
               COALESCE(SUM(cur.credit), 0)             AS credit_total,
               COALESCE(SUM(ytd.debit), 0)              AS cumulative_debit,
               COALESCE(SUM(ytd.credit), 0)             AS cumulative_credit
        FROM t_voucher_entry cur
        INNER JOIN t_voucher v  ON v.id = cur.voucher_id
        INNER JOIN t_subject  s  ON s.id = cur.subject_id
        LEFT JOIN LATERAL (
            SELECT SUM(e.debit) AS debit, SUM(e.credit) AS credit
            FROM t_voucher_entry e
            INNER JOIN t_voucher v2 ON v2.id = e.voucher_id
            WHERE e.subject_id = cur.subject_id
              AND e.assist_json IS NOT NULL
              AND e.assist_json::text = cur.assist_json::text
              AND v2.deleted = 0 AND v2.status = 'POSTED'
              AND v2.period >= CONCAT(LEFT(#{period}, 4), '01') AND v2.period <= #{period}
              AND v2.voucher_no NOT LIKE 'CLOSE-%' AND v2.voucher_no NOT LIKE 'DISTRIB-%'
        ) ytd ON TRUE
        WHERE v.deleted = 0 AND v.status = 'POSTED'
          AND v.period = #{period}
          AND v.voucher_no NOT LIKE 'CLOSE-%' AND v.voucher_no NOT LIKE 'DISTRIB-%'
          AND cur.assist_json IS NOT NULL
          AND cur.assist_json::text <> '{}'
          AND s.aux_calc_type IS NOT NULL
          AND s.deleted = 0
        GROUP BY s.code, s.name, s.aux_calc_type, cur.assist_json::text
        ORDER BY s.code, cur.assist_json::text
    """)
    List<Map<String, Object>> auxiliaryMovement(@Param("period") String period);

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
