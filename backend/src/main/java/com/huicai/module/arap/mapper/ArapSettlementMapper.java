package com.huicai.module.arap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.module.arap.entity.ArapSettlementEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ArapSettlementMapper extends BaseMapper<ArapSettlementEntity> {

    @Delete("DELETE FROM t_arap_settlement")
    int physicalDeleteAll();
}
