package com.huicai.base.voucher.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.base.voucher.dto.AuxiliarySummaryRow;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 凭证分录 Mapper
 */
@Mapper
public interface VoucherEntryMapper extends BaseMapper<VoucherEntryEntity> {

    /**
     * 根据凭证ID查询分录列表
     */
    List<VoucherEntryEntity> selectByVoucherId(@Param("voucherId") Long voucherId);

    /**
     * 按科目 + 会计期间查询分录（JOIN t_voucher 过滤 period）
     * 期间过滤必须在 SQL 层完成，保证账簿查询只返回指定期间分录
     */
    List<VoucherEntryEntity> selectBySubjectIdAndPeriod(@Param("subjectId") Long subjectId, @Param("period") String period);

    /**
     * 辅助核算账 - 本期按核算维度聚合（assist_json ->> dimensionField 分组 SUM 借贷）
     * dimensionValue 为空时按维度值全量分组；非空时仅统计该维度值
     */
    List<AuxiliarySummaryRow> selectAuxiliaryMovement(
            @Param("dimensionField") String dimensionField,
            @Param("dimensionValue") Long dimensionValue,
            @Param("period") String period);

    /**
     * 辅助核算账 - 历史各期（period < 当前期间）按核算维度聚合，用于推算期初余额
     */
    List<AuxiliarySummaryRow> selectAuxiliaryOpening(
            @Param("dimensionField") String dimensionField,
            @Param("dimensionValue") Long dimensionValue,
            @Param("period") String period);

    /**
     * 批量插入分录
     */
    int batchInsert(@Param("list") List<VoucherEntryEntity> entries);

    /**
     * 删除凭证下所有分录
     */
    int deleteByVoucherId(@Param("voucherId") Long voucherId);

    @Delete("DELETE FROM t_voucher_entry WHERE voucher_id IN (SELECT id FROM t_voucher WHERE source = #{source})")
    int deleteByVoucherSource(@Param("source") String source);

    @Delete("DELETE FROM t_voucher_entry")
    int deleteAll();
}
