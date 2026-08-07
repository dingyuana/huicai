package com.huicai.base.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.base.system.entity.PeriodEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 会计期间 Mapper
 */
public interface PeriodMapper extends BaseMapper<PeriodEntity> {

    /**
     * 物理删除软删残留记录（绕开 MyBatis-Plus 逻辑删除，用于释放唯一索引占位）
     */
    @Delete("DELETE FROM t_period WHERE period_code = #{periodCode} AND enterprise_id = #{enterpriseId} AND deleted = 1")
    int purgeSoftDeleted(@Param("periodCode") String periodCode, @Param("enterpriseId") Long enterpriseId);
}
