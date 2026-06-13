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
          SUM(amount) AS amount,
          SUM(tax_amount) AS tax,
          SUM(total_amount) AS total,
          COUNT(*) AS count
        FROM t_output_invoice
        WHERE deleted = 0 AND period = #{period} AND status = 'ISSUED'
    """)
    Map<String, Object> summaryByPeriod(@Param("period") String period);

    @Select("""
        SELECT tax_rate, SUM(tax_amount) AS amount, COUNT(*) AS count
        FROM t_output_invoice
        WHERE deleted = 0 AND period = #{period} AND status = 'ISSUED'
        GROUP BY tax_rate
    """)
    List<Map<String, Object>> byTaxRate(@Param("period") String period);

    @Delete("DELETE FROM t_output_invoice")
    int physicalDeleteAll();

    @Update("UPDATE t_output_invoice SET voucher_id = NULL WHERE voucher_id IS NOT NULL")
    int nullOutVoucherIds();
}
