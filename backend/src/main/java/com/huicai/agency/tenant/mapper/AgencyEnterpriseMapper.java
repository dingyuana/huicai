package com.huicai.agency.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.agency.tenant.entity.AgencyEnterpriseEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AgencyEnterpriseMapper extends BaseMapper<AgencyEnterpriseEntity> {

    @Select("SELECT ae.enterprise_id FROM t_agency_enterprise ae " +
            "JOIN t_enterprise e ON ae.enterprise_id = e.id " +
            "WHERE ae.agency_id = #{agencyId} AND ae.status = 'ACTIVE' AND e.deleted = 0")
    List<Long> getEnterpriseIdsByAgencyId(Long agencyId);
}
