package com.huicai.module.tax.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.module.tax.entity.OutputInvoiceEntity;
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
}
