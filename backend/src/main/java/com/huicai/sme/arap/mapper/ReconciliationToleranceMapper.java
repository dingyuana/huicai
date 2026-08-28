package com.huicai.sme.arap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.sme.arap.entity.ReconciliationToleranceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface ReconciliationToleranceMapper extends BaseMapper<ReconciliationToleranceEntity> {

    /**
     * 按 party 查找生效容差（先查 party-specific，再查全局默认）。
     * 容差类型 ABSOLUTE 走 tolerance_value 作金额上限，PERCENT 走 tolerance_value 作百分比。
     */
    @Select("SELECT t.* FROM t_reconciliation_tolerance t " +
            "WHERE t.deleted = 0 AND t.is_active = true " +
            "AND t.enterprise_id = #{enterpriseId} " +
            "AND ((t.party_id = #{partyId} AND t.party_type = #{partyType}) OR t.party_id IS NULL) " +
            "ORDER BY t.party_id NULLS LAST " +
            "LIMIT 1")
    ReconciliationToleranceEntity findTolerance(@Param("enterpriseId") Long enterpriseId,
                                                 @Param("partyId") Long partyId,
                                                 @Param("partyType") String partyType);

    @Select("SELECT COALESCE(MAX(t.tolerance_value), 5.00) " +
            "FROM t_reconciliation_tolerance t " +
            "WHERE t.deleted = 0 AND t.is_active = true " +
            "AND t.enterprise_id = #{enterpriseId} AND t.party_id IS NULL " +
            "AND t.tolerance_type = 'ABSOLUTE'")
    BigDecimal getDefaultToleranceAmount(@Param("enterpriseId") Long enterpriseId);

    @Select("SELECT COALESCE(MAX(t.tolerance_value), 10.00) " +
            "FROM t_reconciliation_tolerance t " +
            "WHERE t.deleted = 0 AND t.is_active = true " +
            "AND t.enterprise_id = #{enterpriseId} AND t.party_id IS NULL " +
            "AND t.tolerance_type = 'PERCENT'")
    BigDecimal getDefaultToleranceRate(@Param("enterpriseId") Long enterpriseId);
}
