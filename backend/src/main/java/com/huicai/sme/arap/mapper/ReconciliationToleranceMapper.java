package com.huicai.sme.arap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.sme.arap.entity.ReconciliationToleranceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;

@Mapper
public interface ReconciliationToleranceMapper extends BaseMapper<ReconciliationToleranceEntity> {

    @Select("SELECT COALESCE(t.tolerance_amount, 5.00) AS tolerance_amount, " +
            "COALESCE(t.tolerance_rate, 10.00) AS tolerance_rate " +
            "FROM t_reconciliation_tolerance t " +
            "WHERE t.deleted = 0 " +
            "AND t.tenant_id = #{tenantId} " +
            "AND ((t.party_id = #{partyId} AND t.party_type = #{partyType}) OR t.party_id IS NULL) " +
            "AND (t.effective_from IS NULL OR t.effective_from <= #{today}) " +
            "AND (t.effective_to IS NULL OR t.effective_to >= #{today}) " +
            "ORDER BY t.party_id NULLS LAST " +
            "LIMIT 1")
    ReconciliationToleranceEntity findTolerance(@Param("tenantId") Long tenantId,
                                                 @Param("partyId") Long partyId,
                                                 @Param("partyType") String partyType,
                                                 @Param("today") LocalDate today);

    @Select("SELECT t.tolerance_amount FROM t_reconciliation_tolerance t " +
            "WHERE t.deleted = 0 AND t.tenant_id = #{tenantId} AND t.party_id IS NULL")
    BigDecimal getDefaultToleranceAmount(@Param("tenantId") Long tenantId);

    @Select("SELECT t.tolerance_rate FROM t_reconciliation_tolerance t " +
            "WHERE t.deleted = 0 AND t.tenant_id = #{tenantId} AND t.party_id IS NULL")
    BigDecimal getDefaultToleranceRate(@Param("tenantId") Long tenantId);
}
