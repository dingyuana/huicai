package com.huicai.base.voucher.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.base.voucher.entity.VoucherEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 凭证 Mapper
 */
@Mapper
public interface VoucherMapper extends BaseMapper<VoucherEntity> {

    /**
     * 分页查询凭证（含类型名称）
     */
    Page<VoucherEntity> selectVoucherPage(Page<VoucherEntity> page,
                                           @Param("period") String period,
                                           @Param("status") String status,
                                           @Param("voucherTypeId") Long voucherTypeId,
                                           @Param("keyword") String keyword,
                                           @Param("voucherNo") String voucherNo,
                                           @Param("sourceDocNo") String sourceDocNo);

    /**
     * 按ID查询凭证详情
     */
    VoucherEntity selectVoucherDetail(@Param("id") Long id);

    /**
     * 查询指定期间的最大凭证号
     */
    String selectMaxVoucherNo(@Param("period") String period,
                               @Param("voucherTypeId") Long voucherTypeId);

    /**
     * 批量更新凭证状态
     */
    int batchUpdateStatus(@Param("ids") List<Long> ids,
                          @Param("status") String status,
                          @Param("userId") Long userId);

    @Delete("DELETE FROM t_voucher WHERE source = #{source}")
    int deleteBySource(@Param("source") String source);

    @Delete("DELETE FROM t_voucher")
    int deleteAll();

    @Update("UPDATE t_voucher SET business_doc_id = NULL WHERE business_doc_id IS NOT NULL")
    int nullOutBusinessDocId();

    @Select("""
        SELECT v.id, v.voucher_no, v.total_debit, v.total_credit,
               COALESCE(SUM(e.debit), 0) AS actual_debit,
               COALESCE(SUM(e.credit), 0) AS actual_credit
        FROM t_voucher v
        LEFT JOIN t_voucher_entry e ON v.id = e.voucher_id
        WHERE v.deleted = 0
        GROUP BY v.id, v.voucher_no, v.total_debit, v.total_credit
        HAVING ABS(v.total_debit - v.total_credit) > 0.01
            OR ABS(COALESCE(SUM(e.debit), 0) - COALESCE(SUM(e.credit), 0)) > 0.01
        LIMIT 50
    """)
    List<Map<String, Object>> findUnbalancedVouchers();
}
