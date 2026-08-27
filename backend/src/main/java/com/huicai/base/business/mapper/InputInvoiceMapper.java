package com.huicai.base.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.base.business.entity.InputInvoiceEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface InputInvoiceMapper extends BaseMapper<InputInvoiceEntity> {

    @Select("""
        SELECT
          SUM(CASE WHEN declared_status = 'DECLARED' THEN deduction_amount ELSE 0 END) AS deductible,
          SUM(CASE WHEN certification_status = 'CERTIFIED' AND declared_status = 'UNDECLARED' THEN tax_amount ELSE 0 END) AS cert_undeclared,
          SUM(CASE WHEN certification_status = 'UNCERTIFIED' THEN tax_amount ELSE 0 END) AS uncertified,
          SUM(tax_amount) AS total
        FROM t_input_invoice
        WHERE deleted = 0 AND period = #{period}
    """)
    Map<String, Object> summaryByPeriod(@Param("period") String period);

    @Select("""
        SELECT tax_rate, SUM(tax_amount) AS amount, COUNT(*) AS count
        FROM t_input_invoice
        WHERE deleted = 0 AND period = #{period} AND certification_status = 'CERTIFIED'
        GROUP BY tax_rate
    """)
    List<Map<String, Object>> byTaxRate(@Param("period") String period);

    @Delete("DELETE FROM t_input_invoice")
    int physicalDeleteAll();

    @Update("UPDATE t_input_invoice SET doc_id = NULL WHERE doc_id IS NOT NULL")
    int nullOutDocId();

    @Select("""
        SELECT
          vendor_id AS vendorId,
          vendor_name AS vendorName,
          SUM(amount_ex_tax) AS amountExTax,
          SUM(tax_amount) AS taxAmount,
          SUM(total_amount) AS totalAmount,
          tax_rate AS rate,
          certification_status AS certificationStatus,
          declared_status AS declareStatus
        FROM t_input_invoice
        WHERE deleted = 0 AND period = #{period}
          AND (#{vendorId} IS NULL OR vendor_id = #{vendorId})
        GROUP BY vendor_id, vendor_name, tax_rate, certification_status, declared_status
        ORDER BY amount_ex_tax DESC
    """)
    List<Map<String, Object>> appendixIIByVendorAndRate(@Param("period") String period, @Param("vendorId") Long vendorId);
}
