package com.huicai.base.voucher.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.base.voucher.entity.VoucherEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
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
                                           @Param("sourceDocNo") String sourceDocNo,
                                           @Param("subjectId") Long subjectId,
                                           @Param("scope") String scope,
                                           @Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate);

    /**
     * 查询凭证列表（不分页，用于导出）
     */
    List<VoucherEntity> selectVoucherList(
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
     *
     * @param version 乐观锁版本号，非 null 时启用乐观锁检查（单条更新传版本，批量更新传 null）
     */
    int batchUpdateStatus(@Param("ids") List<Long> ids,
                          @Param("status") String status,
                          @Param("userId") Long userId,
                          @Param("version") Integer version);

    /**
     * 按来源删除凭证（逻辑删除，铁律 #12：财务表禁止物理删除）。
     * 注意：分录不随之删除，需调用方另行处理或由 t_bank_statement 式 JOIN 过滤。
     */
    @Update("UPDATE t_voucher SET deleted = 1 WHERE source = #{source} AND deleted = 0")
    int deleteBySource(@Param("source") String source);

    /**
     * 清空全部凭证（逻辑删除，铁律 #12）。当前无生产调用方，保留供数据修复/测试隔离使用。
     */
    @Update("UPDATE t_voucher SET deleted = 1 WHERE deleted = 0")
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