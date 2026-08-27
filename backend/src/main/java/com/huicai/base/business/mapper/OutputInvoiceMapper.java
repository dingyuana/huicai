package com.huicai.base.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.base.business.entity.OutputInvoiceEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    @Update("UPDATE t_output_invoice SET status = #{status}, remark = #{remark}, updated_at = now() WHERE id = #{id} AND deleted = 0")
    int updateStatusDirect(@Param("id") Long id, @Param("status") String status, @Param("remark") String remark);

    @Delete("DELETE FROM t_output_invoice")
    int physicalDeleteAll();

    @Update("UPDATE t_output_invoice SET voucher_id = NULL WHERE voucher_id IS NOT NULL")
    int nullOutVoucherIds();

    @Select("""
        SELECT
          customer_id AS customerId,
          customer_name AS customerName,
          SUM(amount_ex_tax) AS salesAmount,
          SUM(tax_amount) AS taxAmount,
          SUM(total_amount) AS totalAmount,
          tax_rate AS rate
        FROM t_output_invoice
        WHERE deleted = 0 AND period = #{period}
          AND (#{customerId} IS NULL OR customer_id = #{customerId})
        GROUP BY customer_id, customer_name, tax_rate
        ORDER BY total_amount DESC
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
