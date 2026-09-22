package com.huicai.sme.tax.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.sme.tax.dto.vo.InvoiceReconcileVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * P58: 发票-收付款勾稽只读查询.
 * 通过 t_business_doc.invoice_id 关联发票，取 settled_amount 作为已付款。
 */
@Mapper
public interface InvoicePaymentReconcileMapper extends BaseMapper<InvoiceReconcileVO> {

    /** 进项发票勾稽：发票 JOIN 其关联业务单(INVOICE_IN) 的 settled_amount */
    @Select("""
        <script>
        SELECT
            i.id AS invoice_id,
            i.invoice_no AS invoice_no,
            i.invoice_date AS invoice_date,
            i.vendor_name AS vendor_name,
            i.total_amount AS amount,
            i.tax_amount AS tax_amount,
            i.certification_status AS certification_status,
            i.declared_status AS declared_status,
            COALESCE(b.settled_amount, 0) AS paid_amount,
            (i.total_amount - COALESCE(b.settled_amount, 0)) AS unpaid_amount,
            CASE
                WHEN COALESCE(b.settled_amount, 0) &lt;= 0 THEN 'UNPAID'
                WHEN COALESCE(b.settled_amount, 0) &gt;= i.total_amount THEN 'PAID'
                ELSE 'PARTIAL'
            END AS reconcile_status,
            (i.status = 'REVERSED') AS has_red_flushed
        FROM t_input_invoice i
        LEFT JOIN t_business_doc b ON b.invoice_id = i.id AND b.doc_type = 'INVOICE_IN' AND b.deleted = 0
        WHERE i.deleted = 0
        <if test="period != null and period != ''">
          AND i.period = #{period}
        </if>
        <if test="vendorId != null">
          AND i.vendor_id = #{vendorId}
        </if>
        ORDER BY i.invoice_date DESC
        </script>
    """)
    List<InvoiceReconcileVO> queryInputReconcile(@Param("period") String period,
                                                 @Param("vendorId") Long vendorId);

    /** 销项发票勾稽：发票 JOIN 其关联业务单(INVOICE_OUT) 的 settled_amount */
    @Select("""
        <script>
        SELECT
            i.id AS invoice_id,
            i.invoice_no AS invoice_no,
            i.invoice_date AS invoice_date,
            i.customer_name AS customer_name,
            i.total_amount AS amount,
            i.tax_amount AS tax_amount,
            NULL::varchar AS certification_status,
            NULL::varchar AS declared_status,
            COALESCE(b.settled_amount, 0) AS paid_amount,
            (i.total_amount - COALESCE(b.settled_amount, 0)) AS unpaid_amount,
            CASE
                WHEN COALESCE(b.settled_amount, 0) &lt;= 0 THEN 'UNPAID'
                WHEN COALESCE(b.settled_amount, 0) &gt;= i.total_amount THEN 'PAID'
                ELSE 'PARTIAL'
            END AS reconcile_status,
            (i.status = 'REVERSED') AS has_red_flushed
        FROM t_output_invoice i
        LEFT JOIN t_business_doc b ON b.invoice_id = i.id AND b.doc_type = 'INVOICE_OUT' AND b.deleted = 0
        WHERE i.deleted = 0
        <if test="period != null and period != ''">
          AND i.period = #{period}
        </if>
        <if test="customerId != null">
          AND i.customer_id = #{customerId}
        </if>
        ORDER BY i.invoice_date DESC
        </script>
    """)
    List<InvoiceReconcileVO> queryOutputReconcile(@Param("period") String period,
                                                  @Param("customerId") Long customerId);
}
