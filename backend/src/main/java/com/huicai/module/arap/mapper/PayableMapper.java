package com.huicai.module.arap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.module.arap.entity.PayableEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface PayableMapper extends BaseMapper<PayableEntity> {

    @Select("""
        SELECT supplier_id AS vendor_id, SUM(unsettled_amount) AS total_unsettled
        FROM t_business_doc
        WHERE deleted = 0 AND supplier_id IS NOT NULL AND unsettled_amount > 0
        GROUP BY supplier_id
    """)
    List<Map<String, Object>> aggregateByVendor();

    @Select("""
        SELECT
          CASE
            WHEN b.due_date >= CURRENT_DATE THEN 'current'
            WHEN b.due_date >= CURRENT_DATE - INTERVAL '30 days' THEN 'days_0_30'
            WHEN b.due_date >= CURRENT_DATE - INTERVAL '60 days' THEN 'days_31_60'
            WHEN b.due_date >= CURRENT_DATE - INTERVAL '90 days' THEN 'days_61_90'
            WHEN b.due_date >= CURRENT_DATE - INTERVAL '180 days' THEN 'days_91_180'
            WHEN b.due_date >= CURRENT_DATE - INTERVAL '365 days' THEN 'days_181_365'
            ELSE 'over_365'
          END AS aging_bucket,
          SUM(b.unsettled_amount) AS amount,
          COUNT(*) AS count
        FROM t_business_doc b
        WHERE b.deleted = 0 AND b.unsettled_amount > 0
          AND b.supplier_id = #{vendorId}
        GROUP BY aging_bucket
    """)
    List<Map<String, Object>> agingByVendor(@Param("vendorId") Long vendorId);

    @Update("UPDATE t_payable SET doc_id = NULL WHERE doc_id IS NOT NULL")
    int nullOutBusinessDocId();

    @Delete("DELETE FROM t_payable")
    int physicalDeleteAll();
}
