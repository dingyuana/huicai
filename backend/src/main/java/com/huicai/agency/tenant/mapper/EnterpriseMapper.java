package com.huicai.agency.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.agency.tenant.entity.EnterpriseEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EnterpriseMapper extends BaseMapper<EnterpriseEntity> {

    /**
     * 查询企业最近有数据（凭证或余额）的期间，用于计算默认期间。
     */
    @Select("""
            SELECT MAX(p) FROM (
                SELECT period AS p FROM t_subject_balance WHERE deleted = 0 AND enterprise_id = #{enterpriseId}
                UNION ALL
                SELECT period AS p FROM t_voucher WHERE deleted = 0 AND enterprise_id = #{enterpriseId}
            ) x
            """)
    String selectLatestPeriodWithData(@Param("enterpriseId") Long enterpriseId);
}
