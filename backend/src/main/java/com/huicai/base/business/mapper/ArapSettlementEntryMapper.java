package com.huicai.base.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.base.business.entity.ArapSettlementEntryEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ArapSettlementEntryMapper extends BaseMapper<ArapSettlementEntryEntity> {

    @Delete("DELETE FROM t_arap_settlement_entry WHERE receivable_id IS NOT NULL")
    int deleteByReceivableNotNull();

    @Delete("DELETE FROM t_arap_settlement_entry WHERE payable_id IS NOT NULL")
    int deleteByPayableNotNull();

    @Delete("DELETE FROM t_arap_settlement_entry")
    int deleteAll();
}
