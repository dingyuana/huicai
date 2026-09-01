package com.huicai.base.voucher.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.base.voucher.dto.AuxiliarySummaryRow;
import com.huicai.base.voucher.dto.LedgerEntryRowDTO;
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
     * 明细账：按科目 + 会计期间 + 日期范围（可选）查询分录。
     * startDate/endDate 为 null 时退化为期间过滤（与 selectBySubjectIdAndPeriod 等价）。
     * 日期基于 t_voucher.created_at（当前无凭证日期列，created_at 为日期代理，见 SPEC P60 §3 决策）。
     */
    List<VoucherEntryEntity> selectSubsidiaryByDates(
            @Param("subjectId") Long subjectId,
            @Param("period") String period,
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate);

    /**
     * 明细账行投影：JOIN t_voucher 取凭证号/凭证日期，供明细账余额列展示。
     * includeUnposted=false（默认）只含 POSTED 凭证；true 含全部状态（T8）。
     */
    List<LedgerEntryRowDTO> selectSubsidiaryRows(
            @Param("subjectId") Long subjectId,
            @Param("period") String period,
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate,
            @Param("includeUnposted") boolean includeUnposted);

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
