package com.huicai.base.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.base.business.entity.OutputInvoiceEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface OutputInvoiceMapper extends BaseMapper<OutputInvoiceEntity> {

    @Select("""
        SELECT
          COUNT(*) AS "totalCount",
          SUM(amount) AS "totalAmount",
          SUM(CASE WHEN amount < 0 THEN 1 ELSE 0 END) AS "redCount",
          SUM(CASE WHEN status = 'VOIDED' THEN 1 ELSE 0 END) AS "voidedCount",
          SUM(CASE WHEN status = 'REVERSED' THEN 1 ELSE 0 END) AS "reversedCount",
          SUM(CASE WHEN amount >= 0 THEN amount ELSE 0 END) AS "blueAmount",
          SUM(CASE WHEN amount < 0 THEN amount ELSE 0 END) AS "redAmount"
        FROM t_output_invoice
        WHERE deleted = 0
    """)
    Map<String, Object> summaryAll();

    @Select("""
        SELECT
          COUNT(*) AS "totalCount",
          SUM(amount) AS "totalAmount",
          SUM(CASE WHEN amount < 0 THEN 1 ELSE 0 END) AS "redCount",
          SUM(CASE WHEN status = 'VOIDED' THEN 1 ELSE 0 END) AS "voidedCount",
          SUM(CASE WHEN status = 'REVERSED' THEN 1 ELSE 0 END) AS "reversedCount",
          SUM(CASE WHEN amount >= 0 THEN amount ELSE 0 END) AS "blueAmount",
          SUM(CASE WHEN amount < 0 THEN amount ELSE 0 END) AS "redAmount"
        FROM t_output_invoice
        WHERE deleted = 0 AND period = #{period}
    """)
    Map<String, Object> summaryByPeriod(@Param("period") String period);

    @Select("""
        SELECT tax_rate, SUM(tax_amount) AS amount, COUNT(*) AS count
        FROM t_output_invoice
        WHERE deleted = 0 AND period = #{period}
        GROUP BY tax_rate
    """)
    List<Map<String, Object>> byTaxRate(@Param("period") String period);

    /**
     * 销项汇总 — 按列表页筛选条件聚合（对齐 pageQueryOutput 过滤口径）
     * scope: pending=未完成(status NOT IN 终态) / completed=已完成(status IN 终态)
     * invoiceType: RED=红字(amount<0 或 reversed_by_invoice_id 非空)，其他=对应类型且排除红字/已冲销
     */
    @Select("""
        <script>
        SELECT
          COUNT(*) AS "totalCount",
          SUM(amount) AS "totalAmount",
          SUM(CASE WHEN amount &lt; 0 THEN 1 ELSE 0 END) AS "redCount",
          SUM(CASE WHEN status = 'VOIDED' THEN 1 ELSE 0 END) AS "voidedCount",
          SUM(CASE WHEN status = 'REVERSED' THEN 1 ELSE 0 END) AS "reversedCount",
          SUM(CASE WHEN amount &gt;= 0 THEN amount ELSE 0 END) AS "blueAmount",
          SUM(CASE WHEN amount &lt; 0 THEN amount ELSE 0 END) AS "redAmount"
        FROM t_output_invoice
        WHERE deleted = 0
        <if test="customerName != null and customerName != ''">
          AND customer_name LIKE CONCAT('%', #{customerName}, '%')
        </if>
        <if test="period != null and period != '' and startDate == null and endDate == null">
          AND period = #{period}
        </if>
        <if test="status != null and status != ''">
          AND status = #{status}
        </if>
        <if test="scope != null and scope == 'pending'.toString()">
          AND status NOT IN ('VOUCHERED', 'FULLY_RECONCILED', 'PARTIALLY_RECONCILED', 'VOIDED', 'REVERSED')
        </if>
        <if test="scope != null and scope == 'completed'.toString()">
          AND status IN ('VOUCHERED', 'FULLY_RECONCILED', 'PARTIALLY_RECONCILED', 'VOIDED', 'REVERSED')
        </if>
        <if test="startDate != null">
          AND invoice_date &gt;= #{startDate}
        </if>
        <if test="endDate != null">
          AND invoice_date &lt;= #{endDate}
        </if>
        <if test="invoiceType != null and invoiceType == 'RED'.toString()">
          AND (amount &lt; 0 OR reversed_by_invoice_id IS NOT NULL)
        </if>
        <if test="invoiceType != null and invoiceType != '' and invoiceType != 'RED'.toString()">
          AND invoice_type = #{invoiceType}
          AND amount &gt;= 0
          AND reversed_by_invoice_id IS NULL
          AND status != 'REVERSED'
        </if>
        </script>
    """)
    Map<String, Object> summaryByFilter(@Param("customerName") String customerName,
                                        @Param("period") String period,
                                        @Param("status") String status,
                                        @Param("invoiceType") String invoiceType,
                                        @Param("scope") String scope,
                                        @Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate);

    @Update("UPDATE t_output_invoice SET status = #{status}, remark = #{remark}, updated_at = now() WHERE id = #{id} AND deleted = 0")
    int updateStatusDirect(@Param("id") Long id, @Param("status") String status, @Param("remark") String remark);

    @Delete("DELETE FROM t_output_invoice")
    int physicalDeleteAll();

    @Update("UPDATE t_output_invoice SET voucher_id = NULL WHERE voucher_id IS NOT NULL")
    int nullOutVoucherIds();

    @Select("""
        <script>
        SELECT
          customer_id AS customerId,
          customer_name AS customerName,
          SUM(total_amount - tax_amount) AS salesAmount,
          SUM(tax_amount) AS taxAmount,
          SUM(total_amount) AS totalAmount,
          tax_rate AS rate
        FROM t_output_invoice
        WHERE deleted = 0 AND period = #{period}
        <if test="customerId != null">
          AND customer_id = #{customerId}
        </if>
        GROUP BY customer_id, customer_name, tax_rate
        ORDER BY SUM(total_amount) DESC
        </script>
    """)
    List<Map<String, Object>> appendixIByCustomerAndRate(@Param("period") String period, @Param("customerId") Long customerId);

    @Select("""
        SELECT id, invoice_no, status, customer_name
        FROM t_output_invoice
        WHERE deleted = 0
          AND status IN ('VOUCHERED', 'FULLY_RECONCILED', 'PARTIALLY_RECONCILED')
          AND voucher_id IS NULL
        LIMIT 100
    """)
    List<Map<String, Object>> findStatusVoucherIdMismatch();

    @Select("""
        SELECT invoice_no, COUNT(*) AS cnt
        FROM t_output_invoice
        WHERE deleted = 0
        GROUP BY invoice_no
        HAVING COUNT(*) > 1
        LIMIT 50
    """)
    List<Map<String, Object>> findDuplicateInvoiceNos();
}
