package com.huicai.agency.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.agency.tenant.entity.EnterpriseEntity;
import com.huicai.agency.user.entity.AgencyUserEnterpriseEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AgencyUserEnterpriseMapper extends BaseMapper<AgencyUserEnterpriseEntity> {

    @Select("SELECT enterprise_id FROM t_agency_user_enterprise " +
            "WHERE agency_user_id = #{agencyUserId} AND deleted = 0")
    List<Long> getEnterpriseIdsByAgencyUserId(Long agencyUserId);

    @Select("SELECT COUNT(*) FROM t_agency_user_enterprise WHERE agency_user_id = #{userId} AND deleted = 0")
    int countByUserId(@Param("userId") Long userId);

    @Select("SELECT e.* FROM t_agency_user_enterprise aue JOIN t_enterprise e ON aue.enterprise_id = e.id WHERE aue.agency_user_id = #{userId} AND aue.deleted = 0 AND e.deleted = 0")
    List<EnterpriseEntity> selectByUserId(@Param("userId") Long userId);
}
