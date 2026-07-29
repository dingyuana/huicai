package com.huicai.base.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.base.business.entity.BusinessDocEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface BusinessDocMapper extends BaseMapper<BusinessDocEntity> {
    @Delete("DELETE FROM t_business_doc WHERE source = #{source}")
    int deleteBySource(@Param("source") String source);

    @Update("UPDATE t_business_doc SET voucher_id = NULL, status = 'DRAFT' WHERE voucher_id IS NOT NULL")
    int nullOutVoucherIds();

    @Delete("DELETE FROM t_business_doc")
    int physicalDeleteAll();

    @Update("UPDATE t_business_doc SET invoice_id = NULL WHERE invoice_id IS NOT NULL")
    int nullOutInvoiceId();

    @Select("""
        SELECT id, doc_no, status, customer_id, supplier_id
        FROM t_business_doc
        WHERE deleted = 0 AND status = 'VOUCHERED' AND voucher_id IS NULL
        LIMIT 100
    """)
    List<Map<String, Object>> findStatusVoucherIdMismatch();

    @Select("""
        SELECT customer_id, SUM(unsettled_amount) AS total_unsettled
        FROM t_business_doc
        WHERE deleted = 0 AND customer_id IS NOT NULL AND unsettled_amount > 0
        GROUP BY customer_id
    """)
    List<Map<String, Object>> aggregateByCustomer();

    @Select("""
        SELECT b.*, c.name AS customer_name
        FROM t_business_doc b
        LEFT JOIN t_customer c ON c.id = b.customer_id
        WHERE b.deleted = 0
          AND c.deleted = 0
          AND b.unsettled_amount > 0
          AND b.due_date < CURRENT_DATE
        ORDER BY b.due_date ASC
    """)
    List<Map<String, Object>> overdueList();

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
          AND b.customer_id = #{customerId}
        GROUP BY aging_bucket
    """)
    List<Map<String, Object>> agingByCustomer(@Param("customerId") Long customerId);

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

    @Select("""
        SELECT supplier_id, SUM(unsettled_amount) AS total_unsettled
        FROM t_business_doc
        WHERE deleted = 0 AND supplier_id IS NOT NULL AND unsettled_amount > 0
        GROUP BY supplier_id
    """)
    List<Map<String, Object>> aggregateByVendor();

    @Update("UPDATE t_business_doc SET settled_amount = 0, unsettled_amount = amount WHERE deleted = 0")
    int resetSettlementAmounts();

    @Select("SELECT doc_no FROM t_business_doc WHERE doc_no LIKE #{prefix} || '%' AND deleted = 0 ORDER BY doc_no DESC LIMIT 1")
    String selectMaxDocNoByPrefix(@Param("prefix") String prefix);
}
